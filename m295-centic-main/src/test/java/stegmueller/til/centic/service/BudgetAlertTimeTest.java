package stegmueller.til.centic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import stegmueller.til.centic.model.Budget;
import stegmueller.til.centic.model.Category;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.BudgetRepository;
import stegmueller.til.centic.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pflicht 1 (Mocking): Time-Freezing mit Clock.fixed kombiniert mit erweitertem Mocking
 * (ArgumentCaptor, InOrder, verifyNoMoreInteractions, simulierte DB-Fehler).
 * Unit: BudgetService (zusammen mit BudgetServiceTest).
 */
@DisplayName("Budget-Alerts über die Zeit (Clock Mocking + erweitertes Mocking)")
class BudgetAlertTimeTest {

    private final BudgetRepository budgetRepo = mock(BudgetRepository.class);
    private final TransactionRepository transactionRepo = mock(TransactionRepository.class);

    private final User me = User.builder().id(1L).username("til").build();
    private final Category food = Category.builder().id(10L).name("Essen").build();

    private BudgetService serviceAt(String instant, ZoneId zone) {
        return new BudgetService(budgetRepo, transactionRepo, Clock.fixed(Instant.parse(instant), zone));
    }

    private Budget budget(String month, String limit) {
        return Budget.builder().id(1L).month(month).limitAmount(new BigDecimal(limit)).category(food).user(me).build();
    }

    private Transaction expense(String date, String amount) {
        return Transaction.builder().type(TransactionType.EXPENSE).amount(new BigDecimal(amount))
                .date(LocalDate.parse(date)).category(food).user(me).build();
    }

    private String requestedMonth() {
        ArgumentCaptor<String> month = ArgumentCaptor.forClass(String.class);
        verify(budgetRepo).findByUserIdAndMonth(anyLong(), month.capture());
        return month.getValue();
    }

    @Nested
    @DisplayName("Time-Freezing: welcher Monat ist 'jetzt'?")
    class TimeFreezing {

        @ParameterizedTest(name = "{0} ({1}) -> Budgetmonat {2}")
        @CsvSource({
                "2026-01-31T23:59:59Z, UTC, 2026-01",            // letzte Sekunde im Januar
                "2026-02-01T00:00:00Z, UTC, 2026-02",            // Mitternacht: Monatswechsel
                "2026-12-31T23:59:59Z, UTC, 2026-12",            // Silvester
                "2027-01-01T00:00:00Z, UTC, 2027-01",            // Jahreswechsel
                "2028-02-29T12:00:00Z, UTC, 2028-02",            // Schaltjahr
                "2026-04-30T22:30:00Z, Europe/Zurich, 2026-05",  // UTC noch April, in Zürich schon Mai
                "2026-05-01T00:30:00Z, America/New_York, 2026-04" // UTC schon Mai, in New York noch April
        })
        void budgetmonatFolgtDerUhr(String instant, String zone, String expectedMonth) {
            when(budgetRepo.findByUserIdAndMonth(anyLong(), anyString())).thenReturn(List.of());

            serviceAt(instant, ZoneId.of(zone)).checkBudgetAlerts(me);

            assertThat(requestedMonth()).isEqualTo(expectedMonth);
        }

        @Test
        @DisplayName("Mitternacht am Monatsende: Ausgabe vom Vormonat zählt nicht mehr")
        void ausgabeVomVormonatFaelltRaus() {
            when(transactionRepo.findByUserId(me.getId())).thenReturn(List.of(expense("2026-01-31", "500.00")));
            when(budgetRepo.findByUserIdAndMonth(me.getId(), "2026-01")).thenReturn(List.of(budget("2026-01", "100.00")));
            when(budgetRepo.findByUserIdAndMonth(me.getId(), "2026-02")).thenReturn(List.of(budget("2026-02", "100.00")));

            List<Map<String, Object>> beforeMidnight = serviceAt("2026-01-31T23:59:59Z", ZoneOffset.UTC).checkBudgetAlerts(me);
            List<Map<String, Object>> afterMidnight = serviceAt("2026-02-01T00:00:00Z", ZoneOffset.UTC).checkBudgetAlerts(me);

            assertThat(beforeMidnight).hasSize(1);
            assertThat(afterMidnight).isEmpty();
        }

        @Test
        @DisplayName("Schaltjahr: Ausgabe am 29.02. zählt zum Februar-Budget")
        void schalttagGehoertZumFebruar() {
            when(transactionRepo.findByUserId(me.getId())).thenReturn(List.of(
                    expense("2028-02-29", "60.00"), expense("2028-02-01", "50.00"), expense("2028-03-01", "999.00")));
            when(budgetRepo.findByUserIdAndMonth(me.getId(), "2028-02")).thenReturn(List.of(budget("2028-02", "100.00")));

            List<Map<String, Object>> alerts = serviceAt("2028-02-29T23:00:00Z", ZoneOffset.UTC).checkBudgetAlerts(me);

            assertThat(alerts).hasSize(1);
            assertThat((BigDecimal) alerts.get(0).get("spent")).isEqualByComparingTo("110.00");
        }
    }

    @Nested
    @DisplayName("Erweitertes Mocking: Aufrufe und Fehler der Repositories")
    class ErweitertesMocking {

        private final BudgetService service = serviceAt("2026-05-15T10:00:00Z", ZoneOffset.UTC);

        @Test
        @DisplayName("Erst Budgets, dann Transaktionen laden - jeweils genau einmal, mit der User-ID")
        void reihenfolgeUndAnzahlDerAufrufe() {
            when(budgetRepo.findByUserIdAndMonth(1L, "2026-05")).thenReturn(List.of(budget("2026-05", "10.00")));
            when(transactionRepo.findByUserId(1L)).thenReturn(List.of(expense("2026-05-02", "20.00")));

            service.checkBudgetAlerts(me);

            InOrder order = inOrder(budgetRepo, transactionRepo);
            order.verify(budgetRepo, times(1)).findByUserIdAndMonth(1L, "2026-05");
            order.verify(transactionRepo, times(1)).findByUserId(1L);
            verifyNoMoreInteractions(budgetRepo, transactionRepo);
        }

        @Test
        @DisplayName("Alert-Prüfung schreibt nie in die Datenbank")
        void keineSchreibzugriffe() {
            when(budgetRepo.findByUserIdAndMonth(1L, "2026-05")).thenReturn(List.of(budget("2026-05", "10.00")));
            when(transactionRepo.findByUserId(1L)).thenReturn(List.of(expense("2026-05-02", "20.00")));

            service.checkBudgetAlerts(me);

            verify(budgetRepo, never()).save(org.mockito.ArgumentMatchers.any());
            verify(transactionRepo, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("DB-Ausfall beim Laden der Budgets wird weitergegeben, Transaktionen werden nicht mehr geladen")
        void dbFehlerBeiBudgets() {
            when(budgetRepo.findByUserIdAndMonth(anyLong(), anyString()))
                    .thenThrow(new DataAccessResourceFailureException("DB nicht erreichbar"));

            assertThatThrownBy(() -> service.checkBudgetAlerts(me))
                    .isInstanceOf(DataAccessResourceFailureException.class)
                    .hasMessage("DB nicht erreichbar");
            verify(transactionRepo, never()).findByUserId(anyLong());
        }

        @Test
        @DisplayName("DB-Ausfall beim Laden der Transaktionen wird weitergegeben")
        void dbFehlerBeiTransaktionen() {
            when(budgetRepo.findByUserIdAndMonth(anyLong(), anyString())).thenReturn(List.of(budget("2026-05", "10.00")));
            when(transactionRepo.findByUserId(anyLong()))
                    .thenThrow(new DataAccessResourceFailureException("Timeout"));

            assertThatThrownBy(() -> service.checkBudgetAlerts(me))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        }
    }
}

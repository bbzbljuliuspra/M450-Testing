package stegmueller.til.centic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import stegmueller.til.centic.model.Category;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DashboardService")
class DashboardServiceTest {

    // "Heute" ist der 15. Mai 2026
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC);

    private TransactionRepository transactionRepo;
    private DashboardService service;

    private final User me = User.builder().id(1L).username("til").build();
    private final Category food = Category.builder().id(10L).name("Essen").build();
    private final Category rent = Category.builder().id(20L).name("Miete").build();

    @BeforeEach
    void setUp() {
        transactionRepo = mock(TransactionRepository.class);
        service = new DashboardService(transactionRepo, CLOCK);
    }

    private Transaction tx(TransactionType type, String amount, String date, Category category) {
        return Transaction.builder().type(type).amount(new BigDecimal(amount))
                .date(LocalDate.parse(date)).category(category).user(me).build();
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepo.findByUserId(me.getId())).thenReturn(List.of(transactions));
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> part(Map<String, Object> summary, String key) {
        return (Map<String, BigDecimal>) summary.get(key);
    }

    // ---------- Kategorie 4: Diskrete Grenzwerte ----------

    @Nested
    @DisplayName("Diskrete Grenzwerte: Monatsfenster von getMonthlySummary(months)")
    class DiskreteGrenzwerte {

        @ParameterizedTest(name = "months = {0} -> {1} Monate im Fenster")
        @CsvSource({
                "-1, 0",
                " 0, 0",
                " 1, 1",
                " 2, 2",
                "12, 12",
        })
        void anzahlMonateImFenster(int months, int expectedSize) {
            givenTransactions();

            Map<String, Object> result = service.getMonthlySummary(me, months);

            assertThat(part(result, "income")).hasSize(expectedSize);
            assertThat(part(result, "expense")).hasSize(expectedSize);
        }

        @Test
        @DisplayName("months = 1 enthält genau den aktuellen Monat")
        void einMonatIstDerAktuelle() {
            givenTransactions();

            assertThat(part(service.getMonthlySummary(me, 1), "income")).containsOnlyKeys("2026-05");
        }

        @Test
        @DisplayName("months = 3: Fenster geht von 2026-03 bis 2026-05, sortiert")
        void fensterIstSortiertUndLueckenlos() {
            givenTransactions();

            assertThat(part(service.getMonthlySummary(me, 3), "expense").keySet())
                    .containsExactly("2026-03", "2026-04", "2026-05");
        }

        @ParameterizedTest(name = "Ausgabe am {0} bei months = 3 -> gezählt: {1}")
        @CsvSource({
                "2026-02-28, false",  // n-1: einen Tag vor dem ältesten Monat
                "2026-03-01, true",   // n: erster Tag des ältesten Monats
                "2026-03-31, true",
                "2026-05-31, true",   // letzter Tag des aktuellen Monats
                "2026-06-01, false",  // n+1: Zukunft liegt ausserhalb
        })
        void transaktionAnDenFenstergrenzen(String date, boolean counted) {
            givenTransactions(tx(TransactionType.EXPENSE, "10.00", date, food));

            Map<String, BigDecimal> expense = part(service.getMonthlySummary(me, 3), "expense");

            BigDecimal total = expense.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total).isEqualByComparingTo(counted ? "10.00" : "0.00");
        }

        @Test
        @DisplayName("Einnahmen und Ausgaben landen im richtigen Monat und in der richtigen Map")
        void einnahmenUndAusgabenGetrennt() {
            givenTransactions(
                    tx(TransactionType.INCOME, "3000.00", "2026-04-25", null),
                    tx(TransactionType.EXPENSE, "1200.00", "2026-05-01", rent),
                    tx(TransactionType.EXPENSE, "45.50", "2026-05-14", food),
                    tx(TransactionType.INCOME, "100.00", "2026-05-02", null));

            Map<String, Object> result = service.getMonthlySummary(me, 2);

            assertThat(part(result, "income").get("2026-04")).isEqualByComparingTo("3000.00");
            assertThat(part(result, "income").get("2026-05")).isEqualByComparingTo("100.00");
            assertThat(part(result, "expense").get("2026-04")).isEqualByComparingTo("0.00");
            assertThat(part(result, "expense").get("2026-05")).isEqualByComparingTo("1245.50");
            assertThat(result).containsOnlyKeys("income", "expense");
        }
    }

    // ---------- Ergänzung: Sammlungen & Summen ----------

    @Nested
    @DisplayName("Sammlungen: getCategoryStats und getSummary")
    class Sammlungen {

        @Test
        @DisplayName("Ausgaben werden pro Kategorie gruppiert und summiert")
        void gruppiertNachKategorie() {
            givenTransactions(
                    tx(TransactionType.EXPENSE, "20.00", "2026-05-03", food),
                    tx(TransactionType.EXPENSE, "15.25", "2026-05-09", food),
                    tx(TransactionType.EXPENSE, "1200.00", "2026-05-01", rent));

            Map<String, BigDecimal> stats = service.getCategoryStats(me);

            assertThat(stats).containsOnlyKeys("Essen", "Miete");
            assertThat(stats.get("Essen")).isEqualByComparingTo("35.25");
            assertThat(stats.get("Miete")).isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("Einnahmen, Transaktionen ohne Kategorie und andere Monate werden ignoriert")
        void filtertIrrelevanteTransaktionen() {
            givenTransactions(
                    tx(TransactionType.INCOME, "500.00", "2026-05-03", food),
                    tx(TransactionType.EXPENSE, "30.00", "2026-05-03", null),
                    tx(TransactionType.EXPENSE, "70.00", "2026-04-30", food),
                    tx(TransactionType.EXPENSE, "5.00", "2026-05-20", food));

            Map<String, BigDecimal> stats = service.getCategoryStats(me);

            assertThat(stats).containsOnlyKeys("Essen");
            assertThat(stats.get("Essen")).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("Leere Transaktionsliste -> leere Statistik")
        void leereListe() {
            givenTransactions();

            assertThat(service.getCategoryStats(me)).isEmpty();
        }

        @ParameterizedTest(name = "Einnahmen {0} - Ausgaben {1} = Saldo {2}")
        @CsvSource({
                "5000.00, 3200.50, 1799.50",
                "100.00,  100.00,  0.00",
                "0.00,    250.00,  -250.00",
        })
        void saldoIstEinnahmenMinusAusgaben(String income, String expense, String balance) {
            when(transactionRepo.sumAmountByUserIdAndType(me.getId(), TransactionType.INCOME)).thenReturn(new BigDecimal(income));
            when(transactionRepo.sumAmountByUserIdAndType(me.getId(), TransactionType.EXPENSE)).thenReturn(new BigDecimal(expense));

            Map<String, Object> summary = service.getSummary(me);

            assertThat(summary).containsOnlyKeys("totalIncome", "totalExpense", "balance");
            assertThat((BigDecimal) summary.get("totalIncome")).isEqualByComparingTo(income);
            assertThat((BigDecimal) summary.get("totalExpense")).isEqualByComparingTo(expense);
            assertThat((BigDecimal) summary.get("balance")).isEqualByComparingTo(balance);
        }
    }
}

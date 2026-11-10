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
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
 * Pflicht 1 (Mocking): Unit BudgetService, komplett getestet.
 *
 * Alle Abhängigkeiten sind ersetzt, die echte Datenbank wird nie kontaktiert:
 *  - BudgetRepository und TransactionRepository: Mockito-Mocks
 *  - Clock: eingefroren mit Clock.fixed (Time-Freezing)
 *
 * Aufbau:
 *  1. Time-Freezing:              welcher Monat ist "jetzt"?
 *  2. Erweitertes Mocking:        ArgumentCaptor, InOrder, verifyNoMoreInteractions, simulierte DB-Fehler
 *  3. Kombinatorische Logik:      welche Transaktionen zählen zum Budget? (Entscheidungstabelle)
 *  4. Kontinuierliche Grenzwerte: Ausgaben vs. Limit, Rappen-genau
 *  5. CRUD & Fehlerbehandlung:    übrige Methoden, fremde/unbekannte Budgets, Edge Cases
 */
@DisplayName("BudgetService (Pflicht 1: Mocking)")
class BudgetAlertTimeTest {

    // "Heute" ist für alle Tests ohne eigene Uhr der 15. Mai 2026
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String CURRENT_MONTH = "2026-05";

    private final BudgetRepository budgetRepo = mock(BudgetRepository.class);
    private final TransactionRepository transactionRepo = mock(TransactionRepository.class);
    private final BudgetService service = new BudgetService(budgetRepo, transactionRepo, CLOCK);

    private final User me = User.builder().id(1L).username("til").build();
    private final User other = User.builder().id(2L).username("fremd").build();
    private final Category food = Category.builder().id(10L).name("Essen").build();
    private final Category rent = Category.builder().id(20L).name("Miete").build();

    // ---------- Hilfsmethoden ----------

    private BudgetService serviceAt(String instant, ZoneId zone) {
        return new BudgetService(budgetRepo, transactionRepo, Clock.fixed(Instant.parse(instant), zone));
    }

    /** Budget "Essen" für einen beliebigen Monat (Time-Freezing-Tests). */
    private Budget budget(String month, String limit) {
        return Budget.builder().id(1L).month(month).limitAmount(new BigDecimal(limit)).category(food).user(me).build();
    }

    /** Budget im aktuellen Monat mit frei wählbarer Kategorie und Besitzer. */
    private Budget budget(Long id, String limit, Category category, User owner) {
        return Budget.builder().id(id).limitAmount(new BigDecimal(limit))
                .month(CURRENT_MONTH).category(category).user(owner).build();
    }

    private Transaction tx(TransactionType type, String amount, LocalDate date, Category category) {
        return Transaction.builder().type(type).amount(new BigDecimal(amount))
                .date(date).category(category).user(me).build();
    }

    /** Ausgabe "Essen" an einem bestimmten Datum. */
    private Transaction expenseOn(String date, String amount) {
        return tx(TransactionType.EXPENSE, amount, LocalDate.parse(date), food);
    }

    /** Ausgabe im aktuellen Monat (10. Mai 2026). */
    private Transaction expense(String amount, Category category) {
        return tx(TransactionType.EXPENSE, amount, LocalDate.of(2026, 5, 10), category);
    }

    private void givenBudgets(Budget... budgets) {
        when(budgetRepo.findByUserIdAndMonth(me.getId(), CURRENT_MONTH)).thenReturn(List.of(budgets));
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepo.findByUserId(me.getId())).thenReturn(List.of(transactions));
    }

    private String requestedMonth() {
        ArgumentCaptor<String> month = ArgumentCaptor.forClass(String.class);
        verify(budgetRepo).findByUserIdAndMonth(anyLong(), month.capture());
        return month.getValue();
    }

    // ---------- 1. Time-Freezing ----------

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
            when(transactionRepo.findByUserId(me.getId())).thenReturn(List.of(expenseOn("2026-01-31", "500.00")));
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
                    expenseOn("2028-02-29", "60.00"), expenseOn("2028-02-01", "50.00"), expenseOn("2028-03-01", "999.00")));
            when(budgetRepo.findByUserIdAndMonth(me.getId(), "2028-02")).thenReturn(List.of(budget("2028-02", "100.00")));

            List<Map<String, Object>> alerts = serviceAt("2028-02-29T23:00:00Z", ZoneOffset.UTC).checkBudgetAlerts(me);

            assertThat(alerts).hasSize(1);
            assertThat((BigDecimal) alerts.get(0).get("spent")).isEqualByComparingTo("110.00");
        }
    }

    // ---------- 2. Erweitertes Mocking ----------

    @Nested
    @DisplayName("Erweitertes Mocking: Aufrufe und Fehler der Repositories")
    class ErweitertesMocking {

        @Test
        @DisplayName("Erst Budgets, dann Transaktionen laden - jeweils genau einmal, mit der User-ID")
        void reihenfolgeUndAnzahlDerAufrufe() {
            when(budgetRepo.findByUserIdAndMonth(1L, "2026-05")).thenReturn(List.of(budget("2026-05", "10.00")));
            when(transactionRepo.findByUserId(1L)).thenReturn(List.of(expenseOn("2026-05-02", "20.00")));

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
            when(transactionRepo.findByUserId(1L)).thenReturn(List.of(expenseOn("2026-05-02", "20.00")));

            service.checkBudgetAlerts(me);

            verify(budgetRepo, never()).save(any());
            verify(transactionRepo, never()).save(any());
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

    // ---------- 3. Kombinatorische Logik ----------

    @Nested
    @DisplayName("Kombinatorische Logik: welche Transaktionen zählen zum Budget?")
    class KombinatorischeLogik {

        /*
         * Eine Transaktion zählt nur, wenn ALLE Bedingungen erfüllt sind:
         *   Typ == EXPENSE  UND  Kategorie != null  UND  Kategorie == Budget-Kategorie  UND  Datum im aktuellen Monat
         *
         * Spalten: Typ | Kategorie (SAME / OTHER / NONE) | Datum | zählt?
         * Budget-Limit 10.00, Transaktion 50.00 -> zählt sie, entsteht ein Alert mit spent = 50.00
         */
        @ParameterizedTest(name = "{0} / Kategorie {1} / {2} -> zählt: {3}")
        @CsvSource({
                "EXPENSE, SAME,  2026-05-10, true",   // alle Bedingungen erfüllt
                "INCOME,  SAME,  2026-05-10, false",  // nur Typ verletzt
                "EXPENSE, OTHER, 2026-05-10, false",  // nur Kategorie-ID verletzt
                "EXPENSE, NONE,  2026-05-10, false",  // nur Kategorie != null verletzt
                "EXPENSE, SAME,  2026-04-30, false",  // nur Monat verletzt: Tag vor Monatsbeginn
                "EXPENSE, SAME,  2026-05-01, true",   // erster Tag des Monats
                "EXPENSE, SAME,  2026-05-31, true",   // letzter Tag des Monats
                "EXPENSE, SAME,  2026-06-01, false",  // nur Monat verletzt: Tag nach Monatsende
                "EXPENSE, SAME,  2025-05-10, false",  // gleicher Monat, anderes Jahr
                "INCOME,  OTHER, 2026-01-11, false",  // mehrere Bedingungen verletzt
                "INCOME,  NONE,  2026-04-13, false",
        })
        void entscheidungstabelle(TransactionType type, String categoryCase, LocalDate date, boolean counted) {
            Category category = switch (categoryCase) {
                case "SAME" -> food;
                case "OTHER" -> rent;
                case "NONE" -> null;
                default -> throw new IllegalArgumentException(categoryCase);
            };
            givenBudgets(budget(1L, "10.00", food, me));
            givenTransactions(tx(type, "50.00", date, category));

            List<Map<String, Object>> alerts = service.checkBudgetAlerts(me);

            if (counted) {
                assertThat(alerts).hasSize(1);
                assertThat((BigDecimal) alerts.get(0).get("spent")).isEqualByComparingTo("50.00");
            } else {
                assertThat(alerts).isEmpty();
            }
        }

        @Test
        @DisplayName("Jedes Budget summiert nur seine eigene Kategorie")
        void mehrereBudgetsWerdenGetrenntBerechnet() {
            givenBudgets(budget(1L, "100.00", food, me), budget(2L, "200.00", rent, me));
            givenTransactions(expense("80.00", food), expense("150.00", rent), expense("30.00", food));

            List<Map<String, Object>> alerts = service.checkBudgetAlerts(me);

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).get("category")).isEqualTo("Essen");
            assertThat((BigDecimal) alerts.get(0).get("spent")).isEqualByComparingTo("110.00");
        }
    }

    // ---------- 4. Kontinuierliche Grenzwerte ----------

    @Nested
    @DisplayName("Kontinuierliche Grenzwerte: Ausgaben vs. Limit (BigDecimal, Rappen-genau)")
    class KontinuierlicheGrenzwerte {

        @ParameterizedTest(name = "Limit {0}, ausgegeben {1} -> Alert: {2}")
        @CsvSource({
                "100.00,  99.99, false",   // knapp darunter
                "100.00, 100.00, false",   // genau auf dem Limit: noch keine Überschreitung
                "100.00, 100.01, true",    // knapp darüber
                "0.00,     0.01, true",    // Limit 0: jeder Rappen ist zu viel
                "0.00,     0.00, false",
                "999999.99, 1000000.00, true",
        })
        void grenzeUmDasLimit(String limit, String spent, boolean alert) {
            givenBudgets(budget(1L, limit, food, me));
            givenTransactions(expense(spent, food));

            assertThat(service.checkBudgetAlerts(me)).hasSize(alert ? 1 : 0);
        }

        @Test
        @DisplayName("Summe aus Teilbeträgen trifft das Limit exakt (keine Rundungsfehler)")
        void teilbetraegeErgebenGenauDasLimit() {
            givenBudgets(budget(1L, "100.00", food, me));
            givenTransactions(expense("33.33", food), expense("33.33", food), expense("33.34", food));

            assertThat(service.checkBudgetAlerts(me)).isEmpty();
        }

        @Test
        @DisplayName("Alert enthält Kategorie, Limit, Ausgaben und exakten Überschuss")
        void alertWerteSindExakt() {
            givenBudgets(budget(1L, "100.00", food, me));
            givenTransactions(expense("60.00", food), expense("40.01", food));

            Map<String, Object> alert = service.checkBudgetAlerts(me).get(0);

            assertThat(alert).containsOnlyKeys("category", "limit", "spent", "exceeded");
            assertThat(alert.get("category")).isEqualTo("Essen");
            assertThat((BigDecimal) alert.get("limit")).isEqualByComparingTo("100.00");
            assertThat((BigDecimal) alert.get("spent")).isEqualByComparingTo("100.01");
            assertThat((BigDecimal) alert.get("exceeded")).isEqualByComparingTo("0.01");
        }
    }

    // ---------- 5. CRUD & Fehlerbehandlung ----------

    @Nested
    @DisplayName("CRUD, Fehlerbehandlung & Edge Cases")
    class Fehlerbehandlung {

        @Test
        @DisplayName("Unbekannte Budget-ID -> NoSuchElementException mit ID in der Meldung")
        void unbekannteId() {
            when(budgetRepo.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByIdForUser(42L, me))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("Budget nicht gefunden: 42");
        }

        @Test
        @DisplayName("Budget eines fremden Users ist nicht sichtbar")
        void fremdesBudget() {
            when(budgetRepo.findById(5L)).thenReturn(Optional.of(budget(5L, "50.00", food, other)));

            assertThatThrownBy(() -> service.getByIdForUser(5L, me))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("Budget nicht gefunden: 5");
        }

        @Test
        @DisplayName("Eigenes Budget wird zurückgegeben")
        void eigenesBudget() {
            Budget mine = budget(5L, "50.00", food, me);
            when(budgetRepo.findById(5L)).thenReturn(Optional.of(mine));

            assertThat(service.getByIdForUser(5L, me)).isSameAs(mine);
        }

        @Test
        @DisplayName("updateLimit ändert nur das Limit und speichert")
        void updateLimitEigenesBudget() {
            Budget mine = budget(5L, "50.00", food, me);
            when(budgetRepo.findById(5L)).thenReturn(Optional.of(mine));
            when(budgetRepo.save(mine)).thenReturn(mine);
            Budget changes = Budget.builder().limitAmount(new BigDecimal("75.50")).month("1999-01").build();

            Budget result = service.updateLimit(5L, changes, me);

            assertThat(result).isSameAs(mine);
            assertThat(mine.getLimitAmount()).isEqualByComparingTo("75.50");
            assertThat(mine.getMonth()).isEqualTo(CURRENT_MONTH);
            verify(budgetRepo).save(mine);
        }

        @Test
        @DisplayName("updateLimit auf fremdes Budget -> Exception, nichts gespeichert")
        void updateLimitFremdesBudget() {
            when(budgetRepo.findById(5L)).thenReturn(Optional.of(budget(5L, "50.00", food, other)));

            assertThatThrownBy(() -> service.updateLimit(5L, budget(null, "1.00", food, me), me))
                    .isInstanceOf(NoSuchElementException.class);
            verify(budgetRepo, never()).save(any());
        }

        @Test
        @DisplayName("delete löscht eigenes Budget")
        void deleteEigenesBudget() {
            Budget mine = budget(5L, "50.00", food, me);
            when(budgetRepo.findById(5L)).thenReturn(Optional.of(mine));

            service.delete(5L, me);

            verify(budgetRepo).delete(mine);
        }

        @Test
        @DisplayName("delete mit unbekannter ID -> Exception, nichts gelöscht")
        void deleteUnbekannt() {
            when(budgetRepo.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(9L, me)).isInstanceOf(NoSuchElementException.class);
            verify(budgetRepo, never()).delete(any());
        }

        @Test
        @DisplayName("create setzt den aktuellen User als Besitzer")
        void createSetztBesitzer() {
            Budget input = budget(null, "20.00", food, other);
            when(budgetRepo.save(input)).thenReturn(input);

            Budget result = service.create(input, me);

            assertThat(result).isSameAs(input);
            assertThat(input.getUser()).isSameAs(me);
        }

        @Test
        @DisplayName("getAllForUser liefert die Budgets des Users")
        void getAllForUser() {
            List<Budget> budgets = List.of(budget(1L, "10.00", food, me));
            when(budgetRepo.findByUserId(me.getId())).thenReturn(budgets);

            assertThat(service.getAllForUser(me)).isSameAs(budgets);
        }

        @Test
        @DisplayName("Keine Budgets im Monat -> leere Alert-Liste")
        void keineBudgets() {
            givenBudgets();
            givenTransactions(expense("500.00", food));

            assertThat(service.checkBudgetAlerts(me)).isEmpty();
        }

        @Test
        @DisplayName("Budget ohne Transaktionen -> kein Alert")
        void keineTransaktionen() {
            givenBudgets(budget(1L, "10.00", food, me));
            givenTransactions();

            assertThat(service.checkBudgetAlerts(me)).isEmpty();
        }

        @Test
        @DisplayName("Bekannter Fehler: Budget ohne Kategorie führt zu NullPointerException")
        void budgetOhneKategorie() {
            givenBudgets(budget(1L, "10.00", null, me));
            givenTransactions(expense("50.00", food));

            // Dokumentiert das aktuelle Verhalten. Siehe docs/Fehlerprotokoll.md
            assertThatThrownBy(() -> service.checkBudgetAlerts(me)).isInstanceOf(NullPointerException.class);
        }
    }
}

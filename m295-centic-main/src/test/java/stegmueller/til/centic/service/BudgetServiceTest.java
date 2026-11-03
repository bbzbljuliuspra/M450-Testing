package stegmueller.til.centic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BudgetService")
class BudgetServiceTest {

    // "Heute" ist für alle Tests der 15. Mai 2026
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String CURRENT_MONTH = "2026-05";

    private BudgetRepository budgetRepo;
    private TransactionRepository transactionRepo;
    private BudgetService service;

    private final User me = User.builder().id(1L).username("til").build();
    private final User other = User.builder().id(2L).username("fremd").build();
    private final Category food = Category.builder().id(10L).name("Essen").build();
    private final Category rent = Category.builder().id(20L).name("Miete").build();

    @BeforeEach
    void setUp() {
        budgetRepo = mock(BudgetRepository.class);
        transactionRepo = mock(TransactionRepository.class);
        service = new BudgetService(budgetRepo, transactionRepo, CLOCK);
    }

    // ---------- Hilfsmethoden ----------

    private Budget budget(Long id, String limit, Category category, User owner) {
        return Budget.builder().id(id).limitAmount(new BigDecimal(limit))
                .month(CURRENT_MONTH).category(category).user(owner).build();
    }

    private Transaction tx(TransactionType type, String amount, LocalDate date, Category category) {
        return Transaction.builder().type(type).amount(new BigDecimal(amount))
                .date(date).category(category).user(me).build();
    }

    private Transaction expense(String amount, Category category) {
        return tx(TransactionType.EXPENSE, amount, LocalDate.of(2026, 5, 10), category);
    }

    private void givenBudgets(Budget... budgets) {
        when(budgetRepo.findByUserIdAndMonth(me.getId(), CURRENT_MONTH)).thenReturn(List.of(budgets));
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepo.findByUserId(me.getId())).thenReturn(List.of(transactions));
    }

    // ---------- Kategorie 1: Kombinatorische Logik ----------

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

    // ---------- Kategorie 2: Kontinuierliche Grenzwerte ----------

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

    // ---------- Kategorie 3: Fehlerbehandlung & Edge Cases ----------

    @Nested
    @DisplayName("Fehlerbehandlung & Edge Cases")
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

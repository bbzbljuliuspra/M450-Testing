package stegmueller.til.centic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import stegmueller.til.centic.model.BudgetStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pflicht 2 (Logik): BudgetStatusCalculator wird auf Herz und Nieren getestet
 * (Validierung, Grenzwerte, Prognose). Die Tests wurden per TDD geschrieben, bevor die Klasse existierte.
 *
 * Regeln (Budget-Ampel):
 *  - Ausgaben > Limit                          -> ROT
 *  - Ausgaben >= 80 % des Limits (bis 100 %)   -> GELB
 *  - sonst, im laufenden Monat: lineare Prognose bis Monatsende > Limit -> PROGNOSE_UEBERSCHREITUNG
 *  - sonst                                     -> GRUEN
 *  - Monat muss Format yyyy-MM haben, Limit > 0, Ausgaben >= 0, nichts darf null sein
 */
@DisplayName("BudgetStatusCalculator (Logik)")
class BudgetStatusCalculatorTest {

    // "Heute" ist Freitag, 15. Mai 2026 (Mai hat 31 Tage)
    private final BudgetStatusCalculator calculator = calculatorAt("2026-05-15T12:00:00Z");

    private static BudgetStatusCalculator calculatorAt(String instant) {
        return new BudgetStatusCalculator(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private BudgetStatus evaluate(String limit, String spent, String month) {
        return calculator.evaluate(new BigDecimal(limit), new BigDecimal(spent), month);
    }

    @Nested
    @DisplayName("Ampel-Schwellen (vergangener Monat, also ohne Prognose)")
    class Schwellen {

        @ParameterizedTest(name = "Limit {0}, ausgegeben {1} -> {2}")
        @CsvSource({
                "100.00,   0.00, GRUEN",
                "100.00,  79.99, GRUEN",   // knapp unter 80 %
                "100.00,  80.00, GELB",    // genau 80 %
                "100.00,  80.01, GELB",
                "100.00,  99.99, GELB",
                "100.00, 100.00, GELB",    // genau auf dem Limit ist noch nicht überschritten
                "100.00, 100.01, ROT",     // knapp darüber
                "100.00, 500.00, ROT",
                "0.01,     0.01, GELB",    // kleinstes Limit
                "0.01,     0.02, ROT",
                "250.00, 199.99, GRUEN",   // 80 % von 250 = 200
                "250.00, 200.00, GELB",
        })
        void ampel(String limit, String spent, BudgetStatus expected) {
            assertThat(evaluate(limit, spent, "2026-04")).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Prognose bis Monatsende (laufender Monat, Clock eingefroren)")
    class Prognose {

        @ParameterizedTest(name = "Limit 100, am 15.05. ausgegeben {0} -> {1}")
        @CsvSource({
                "40.00, GRUEN",                       // 40 / 15 * 31 = 82.67
                "48.38, GRUEN",                       // 48.38 * 31 = 1499.78 <= 1500
                "48.39, PROGNOSE_UEBERSCHREITUNG",    // 48.39 * 31 = 1500.09 >  1500
                "79.99, PROGNOSE_UEBERSCHREITUNG",
                "80.00, GELB",                        // GELB hat Vorrang vor der Prognose
                "100.01, ROT",                        // ROT hat Vorrang vor allem
        })
        void prognoseMitteDesMonats(String spent, BudgetStatus expected) {
            assertThat(evaluate("100.00", spent, "2026-05")).isEqualTo(expected);
        }

        @Test
        @DisplayName("Prognose genau auf dem Limit ist noch keine Überschreitung")
        void prognoseGenauAufDemLimit() {
            // Limit 31.00 am 15.05.: 15.00 * 31 = 465 = 31.00 * 15 -> Prognose exakt 31.00
            assertThat(evaluate("31.00", "15.00", "2026-05")).isEqualTo(BudgetStatus.GRUEN);
            assertThat(evaluate("31.00", "15.01", "2026-05")).isEqualTo(BudgetStatus.PROGNOSE_UEBERSCHREITUNG);
        }

        @Test
        @DisplayName("Am 1. des Monats: schon 5 % ausgegeben ergibt Prognose 155 %")
        void ersterTagDesMonats() {
            BudgetStatusCalculator firstDay = calculatorAt("2026-05-01T08:00:00Z");

            assertThat(firstDay.evaluate(new BigDecimal("100.00"), new BigDecimal("5.00"), "2026-05"))
                    .isEqualTo(BudgetStatus.PROGNOSE_UEBERSCHREITUNG);
            assertThat(firstDay.evaluate(new BigDecimal("100.00"), new BigDecimal("3.22"), "2026-05"))
                    .isEqualTo(BudgetStatus.GRUEN);   // 3.22 * 31 = 99.82
        }

        @Test
        @DisplayName("Am letzten Tag entspricht die Prognose den tatsächlichen Ausgaben")
        void letzterTagDesMonats() {
            BudgetStatusCalculator lastDay = calculatorAt("2026-05-31T22:00:00Z");

            assertThat(lastDay.evaluate(new BigDecimal("100.00"), new BigDecimal("79.99"), "2026-05"))
                    .isEqualTo(BudgetStatus.GRUEN);
        }

        @Test
        @DisplayName("Schaltjahr: Februar 2028 hat 29 Tage")
        void schaltjahrFebruar() {
            BudgetStatusCalculator day14 = calculatorAt("2028-02-14T12:00:00Z");

            // 48.27 * 29 = 1399.83 <= 1400 ; 48.28 * 29 = 1400.12 > 1400
            assertThat(day14.evaluate(new BigDecimal("100.00"), new BigDecimal("48.27"), "2028-02"))
                    .isEqualTo(BudgetStatus.GRUEN);
            assertThat(day14.evaluate(new BigDecimal("100.00"), new BigDecimal("48.28"), "2028-02"))
                    .isEqualTo(BudgetStatus.PROGNOSE_UEBERSCHREITUNG);
        }

        @ParameterizedTest(name = "Monat {0} ist nicht der laufende -> keine Prognose")
        @ValueSource(strings = {"2026-04", "2026-06", "2025-05"})
        void keinePrognoseAusserhalbDesLaufendenMonats(String month) {
            assertThat(evaluate("100.00", "79.99", month)).isEqualTo(BudgetStatus.GRUEN);
        }
    }

    @Nested
    @DisplayName("Auslastung in Prozent")
    class Auslastung {

        @ParameterizedTest(name = "Limit {0}, ausgegeben {1} -> {2} %")
        @CsvSource({
                "100.00,   0.00,   0.00",
                "100.00,  80.00,  80.00",
                "300.00, 100.00,  33.33",
                "3.00,     2.00,  66.67",   // kaufmännisch gerundet
                "100.00, 150.00, 150.00",
                "8.00,     1.00,  12.50",
        })
        void prozent(String limit, String spent, String expected) {
            assertThat(calculator.usagePercent(new BigDecimal(limit), new BigDecimal(spent)))
                    .isEqualByComparingTo(expected)
                    .hasScaleOf(2);
        }
    }

    @Nested
    @DisplayName("Validierung: Monatsformat (Regex)")
    class Monatsformat {

        @ParameterizedTest(name = "\"{0}\" ist gültig")
        @ValueSource(strings = {"2026-01", "2026-09", "2026-10", "2026-12", "1999-07"})
        void gueltigeMonate(String month) {
            assertThat(evaluate("100.00", "1.00", month)).isNotNull();
        }

        @ParameterizedTest(name = "\"{0}\" ist ungültig")
        @ValueSource(strings = {"2026-00", "2026-13", "2026-5", "26-05", "2026/05", "2026-05-01",
                "", " 2026-05", "2026-05 ", "abcd-ef", "20260-05", "2026-1a"})
        void ungueltigeMonate(String month) {
            assertThatThrownBy(() -> evaluate("100.00", "1.00", month))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ungültiger Monat: '" + month + "' (erwartet yyyy-MM)");
        }
    }

    @Nested
    @DisplayName("Validierung: Fehlerbehandlung")
    class Fehlerbehandlung {

        @Test
        void limitNull() {
            assertThatThrownBy(() -> calculator.evaluate(null, BigDecimal.ONE, "2026-05"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit darf nicht null sein");
        }

        @Test
        void ausgabenNull() {
            assertThatThrownBy(() -> calculator.evaluate(BigDecimal.TEN, null, "2026-05"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ausgaben dürfen nicht null sein");
        }

        @Test
        void monatNull() {
            assertThatThrownBy(() -> calculator.evaluate(BigDecimal.TEN, BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Monat darf nicht null sein");
        }

        @ParameterizedTest(name = "Limit {0} ist unzulässig")
        @ValueSource(strings = {"0.00", "-0.01", "-100"})
        void limitMussPositivSein(String limit) {
            assertThatThrownBy(() -> evaluate(limit, "1.00", "2026-05"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit muss grösser als 0 sein");
        }

        @Test
        @DisplayName("Negative Ausgaben sind unzulässig")
        void negativeAusgaben() {
            assertThatThrownBy(() -> evaluate("100.00", "-0.01", "2026-05"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ausgaben dürfen nicht negativ sein");
        }

        @Test
        @DisplayName("usagePercent validiert ebenfalls das Limit")
        void prozentMitUngueltigemLimit() {
            assertThatThrownBy(() -> calculator.usagePercent(BigDecimal.ZERO, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Limit muss grösser als 0 sein");
        }
    }
}

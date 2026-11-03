# Fehlerprotokoll LB1 (Vorlage M450_LB1_Unittest_Fehler.xlsx)

Inhalt zum Übertragen in die Excel-Vorlage der Lehrperson.

## Fehler 1 – TDD: Budget genau auf dem Limit wird als ROT bewertet

| Feld | Inhalt |
|---|---|
| Nr. | 1 |
| Datum | 03.11.2026 |
| Unit | `BudgetStatusCalculator.evaluate(limit, spent, month)` |
| Test | `BudgetStatusCalculatorTest.Schwellen.ampel` Zeilen [6] und [9] |
| Testdaten | Limit 100.00 / Ausgaben 100.00; Limit 0.01 / Ausgaben 0.01; Monat 2026-04 |
| Erwartet | `GELB` (Limit erreicht, aber nicht überschritten) |
| Tatsächlich | `ROT` |
| Test-Output | `expected: GELB but was: ROT` |
| Ursache | Vergleich `spent.compareTo(limit) >= 0` statt `> 0`. Damit gilt bereits „Ausgaben = Limit“ als Überschreitung (Off-by-one an der oberen Grenze). |
| Korrekturmassnahme | Bedingung in `> 0` ändern: ROT nur, wenn die Ausgaben das Limit echt übersteigen. Konsistent mit `BudgetService.checkBudgetAlerts`, das ebenfalls `> 0` verwendet. |
| Schwere | Mittel (Nutzer wird fälschlich gewarnt, keine Datenverfälschung) |
| Status | Behoben (Commit „TDD Refactor: Grenzfehler behoben“) |
| Gefunden durch | Unit-Test (Grenzwertanalyse n / n+1 am Limit) |

## Fehler 2 – Bestehender Code: Budget ohne Kategorie bringt die Alert-Prüfung zum Absturz

| Feld | Inhalt |
|---|---|
| Nr. | 2 |
| Datum | 03.11.2026 |
| Unit | `BudgetService.checkBudgetAlerts(user)` |
| Test | `BudgetServiceTest.Fehlerbehandlung.budgetOhneKategorie` |
| Testdaten | Budget mit `category = null`, eine Ausgabe in „Essen“ im aktuellen Monat |
| Erwartet | Budget wird ignoriert oder ein verständlicher Fehler (z. B. 400 Bad Request) |
| Tatsächlich | `NullPointerException` bei `budget.getCategory().getId()` (HTTP 500 auf `/api/budgets/check`) |
| Ursache | `Budget.category` ist nicht `@NotNull`, die Prüfung greift aber ungeprüft darauf zu. |
| Korrekturmassnahme | `@NotNull` auf `Budget.category` setzen (Validierung beim Erstellen) und in `checkBudgetAlerts` Budgets ohne Kategorie überspringen. Danach Test auf neues Verhalten anpassen. |
| Schwere | Hoch (ganzer Endpoint fällt für den User aus) |
| Status | Offen |
| Gefunden durch | Unit-Test (Edge Case `null`) |

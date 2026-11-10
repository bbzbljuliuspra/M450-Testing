# M450 LB1 – Unit-Testing (Centic)

Testobjekt ist `m295-centic-main`, ein Spring-Boot-Backend für persönliche Finanzen.
Alle Befehle laufen im Ordner `m295-centic-main`:

```bash
cd m295-centic-main
```

Voraussetzung: Java 26. Maven ist nicht nötig, der Wrapper `./mvnw` lädt es selbst.

## Tests ausführen

```bash
./mvnw test
```

Ergebnis steht am Ende: `Tests run: …, Failures: 0`.
Details pro Testklasse: `target/surefire-reports/`.

## Mutation Testing (PIT)

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Report öffnen:

```bash
open target/pit-reports/index.html
```

(Windows: `start target\pit-reports\index.html`)

Im Report:
- **Line Coverage**: welche Zeilen von Tests ausgeführt werden.
- **Mutation Coverage**: welcher Anteil der eingebauten Fehler (Mutanten) von den Tests gefunden wurde. Ziel: 100 %.
- Klick auf eine Klasse → pro Zeile die Mutationen, z. B. `changed conditional boundary → KILLED`.

Fällt der Score unter 100 %, bricht der Build ab (`mutationThreshold` in `pom.xml`).
Geprüfte Klassen stehen in der `pom.xml` unter `targetClasses`.

## Automatisch auf GitHub

Bei jedem Push laufen Tests und PIT über GitHub Actions (`.github/workflows/tests.yml`).

Reports herunterladen: GitHub → **Actions** → Lauf auswählen → unten **Artifacts** → `test-reports`.
Darin liegen `surefire-reports/` und `pit-reports/index.html`.

## Übersicht der Tests

| Teil | Datei | Inhalt |
|---|---|---|
| Pflicht 1 (Mocking) – Unit `BudgetService` | `BudgetAlertTimeTest` | Clock Mocking (Time-Freezing) und erweitertes Mocking |
| Pflicht 1 (Mocking) – Unit `BudgetService` | `BudgetServiceTest` | Repos gemockt, Kategorie 1–3: Kombinatorische Logik, kontinuierliche Grenzwerte, Fehlerbehandlung |
| Pflicht 2 (Logik) – Unit `BudgetStatusCalculator` | `BudgetStatusCalculatorTest` | Budget-Ampel: Validierung, Grenzwerte, Prognose (per TDD entstanden) |
| TDD Statistik – Unit `StatistikService` | `StatistikServiceTest` | Durchschnittliche Username-Länge |
| Mutation Testing | `pom.xml` (PIT) | prüft die Units aus Pflicht 1 und 2 sowie die TDD-Unit |

Gefundene Fehler: `docs/Fehlerprotokoll.md`.

## Gegenprobe (Auf Branch "Gegenprobe" - Soll nicht gemerged werden)

1. In `BudgetService.checkBudgetAlerts` den Vergleich `> 0` zu `>= 0` ändern.
2. `./mvnw test` → Test `grenzeUmDasLimit [2]` wird rot (`Expected size: 0 but was: 1`).
3. Änderung zurücksetzen:

```bash
git checkout -- src/main/java/stegmueller/til/centic/service/BudgetService.java
```

# Centic - M295 Projektarbeit

Das Projekt realisiert ein REST-Backend zur Verwaltung persönlicher Finanzen. Nutzer 
können ihre täglichen Transaktionen (Einnahmen und Ausgaben) erfassen, 
kategorisieren und Budgets festlegen. Das System berechnet automatisch den 
aktuellen Kontostand und prüft, ob gesetzte Budget Limits überschritten werden. 
## 🚀 Features

* **Transaktionsmanagement**: Vollständiges CRUD für Buchungen (Betrag, Datum, Zweck).
* **Kategorisierung**: Verwaltung von Kategorien (z. B. "Essen", "Miete") mit Farbcodes.
* **Budget-Monitoring**: Monatliche Limits setzen und automatisierte Prüfung auf Überschreitungen.
* **Finanz-Dashboard**: Berechnete Daten wie Gesamtsaldo und monatliche Ausgaben-Analysen.
* **Sicherheit**: Rollenbasierte Zugriffskontrolle (RBAC) via Keycloak (OAuth2/JWT).
* **API-Dokumentation**: Interaktive Swagger UI zur Live-Testung der Endpunkte.

## 🛠️ Tech Stack

* **Sprache**: Java 24
* **Framework**: Spring Boot 4
* **Datenbank**: PostgreSQL
* **Security**: Keycloak (OIDC)
* **Dokumentation**: SpringDoc OpenAPI (Swagger UI)

## 📋 Voraussetzungen & Setup

### 1. Datenbank (PostgreSQL)
Stellen Sie sicher, dass eine PostgreSQL-Instanz läuft und eine leere Datenbank namens `centic` existiert.

### 2. Keycloak Setup
* Importieren Sie die beiliegende `realm-export.json` in Ihren Keycloak-Server.
* Realm-Name: `ILV`
* Client-ID: `centic`
* Stellen Sie sicher, dass die Rollen `ROLE_USER` und `ROLE_ADMIN` vorhanden sind.

### 3. Applikation starten
Klonen Sie das Repository und führen Sie den Maven-Build aus:

```bash
git clone https://github.com/tillit92/m295-centic.git
cd m295-centic
mvn clean install
mvn spring-boot:run

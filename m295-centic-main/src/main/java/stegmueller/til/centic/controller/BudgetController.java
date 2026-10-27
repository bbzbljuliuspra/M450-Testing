package stegmueller.til.centic.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import stegmueller.til.centic.model.Budget;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.security.CurrentUserResolver;
import stegmueller.til.centic.service.BudgetService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Verwaltung der monatlichen Budgetlimits pro Kategorie")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class BudgetController {

    private final BudgetService budgetService;
    private final CurrentUserResolver userResolver;

    @GetMapping
    @Operation(
            summary = "Alle eigenen Budgets laden",
            description = "Gibt eine Liste aller Budgets zurück, die der aktuell angemeldete Benutzer erstellt hat."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Budgets erfolgreich geladen")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Nicht authentifiziert")
    public List<Budget> getAll() {
        return budgetService.getAllForUser(userResolver.getCurrentUser());
    }

    @PostMapping
    @Operation(summary = "Budget für Kategorie + Monat setzen")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Budget erfolgreich erstellt")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validierungsfehler in den Eingabedaten")
    public ResponseEntity<Budget> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Das zu erstellende Budget-Objekt")
            @Valid @RequestBody Budget budget) {
        User me = userResolver.getCurrentUser();
        Budget saved = budgetService.create(budget, me);
        return ResponseEntity
                .created(URI.create("/api/budgets/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Budget-Limit ändern")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Budget erfolgreich aktualisiert")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Budget mit dieser ID nicht gefunden")
    public ResponseEntity<Budget> update(
            @io.swagger.v3.oas.annotations.Parameter(description = "Eindeutige ID des Budgets", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody Budget updated) {
        User me = userResolver.getCurrentUser();
        return ResponseEntity.ok(budgetService.updateLimit(id, updated, me));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Budget löschen")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Budget erfolgreich gelöscht")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Zugriff verweigert (gehört nicht dem User)")
    public ResponseEntity<Void> delete(
            @io.swagger.v3.oas.annotations.Parameter(description = "ID des zu löschenden Budgets")
            @PathVariable Long id) {
        budgetService.delete(id, userResolver.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check")
    @Operation(
            summary = "Prüfe Budget-Überschreitungen",
            description = "Vergleicht die Ausgaben des aktuellen Monats mit den gesetzten Limits."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Analyse erfolgreich durchgeführt")
    public List<Map<String, Object>> checkBudgets() {
        return budgetService.checkBudgetAlerts(userResolver.getCurrentUser());
    }
}
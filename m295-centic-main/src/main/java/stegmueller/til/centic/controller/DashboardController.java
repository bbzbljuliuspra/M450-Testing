package stegmueller.til.centic.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import stegmueller.til.centic.security.CurrentUserResolver;
import stegmueller.til.centic.service.DashboardService;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Berechnete Finanzdaten")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserResolver userResolver;

    @GetMapping("/summary")
    @Operation(summary = "Gesamtsaldo und Einnahmen/Ausgaben-Summen")
    public Map<String, Object> getSummary() {
        return dashboardService.getSummary(userResolver.getCurrentUser());
    }

    @GetMapping("/monthly")
    @Operation(summary = "Ausgaben und Einnahmen pro Monat")
    public Map<String, Object> getMonthly(
            @RequestParam(defaultValue = "6") int months) {
        return dashboardService.getMonthlySummary(userResolver.getCurrentUser(), months);
    }

    @GetMapping("/category-stats")
    @Operation(summary = "Ausgaben pro Kategorie im aktuellen Monat")
    public Map<String, BigDecimal> getCategoryStats() {
        return dashboardService.getCategoryStats(userResolver.getCurrentUser());
    }
}
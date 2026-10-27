package stegmueller.til.centic.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.security.CurrentUserResolver;
import stegmueller.til.centic.service.TransactionService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Buchungen verwalten")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class TransactionController {

    private final TransactionService transactionService;
    private final CurrentUserResolver userResolver;

    @GetMapping
    @Operation(summary = "Alle eigenen Transaktionen laden")
    public List<Transaction> getAll() {
        User me = userResolver.getCurrentUser();
        return transactionService.getAllForUser(me);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Einzelne Transaktion laden")
    public ResponseEntity<Transaction> getById(@PathVariable Long id) {
        User me = userResolver.getCurrentUser();
        return ResponseEntity.ok(transactionService.getByIdForUser(id, me));
    }

    @PostMapping
    @Operation(summary = "Neue Transaktion anlegen")
    public ResponseEntity<Transaction> create(@Valid @RequestBody Transaction transaction) {
        User me = userResolver.getCurrentUser();
        Transaction saved = transactionService.create(transaction, me);
        return ResponseEntity
                .created(URI.create("/api/transactions/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Transaktion aktualisieren")
    public ResponseEntity<Transaction> update(@PathVariable Long id,
                                              @Valid @RequestBody Transaction updated) {
        User me = userResolver.getCurrentUser();
        return ResponseEntity.ok(transactionService.update(id, updated, me));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Transaktion löschen")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User me = userResolver.getCurrentUser();
        transactionService.delete(id, me);
        return ResponseEntity.noContent().build();
    }
}
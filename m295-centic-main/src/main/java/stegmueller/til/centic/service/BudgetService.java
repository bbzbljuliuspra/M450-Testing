package stegmueller.til.centic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import stegmueller.til.centic.model.Budget;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.BudgetRepository;
import stegmueller.til.centic.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepo;
    private final TransactionRepository transactionRepo;

    public List<Budget> getAllForUser(User user) {
        return budgetRepo.findByUserId(user.getId());
    }

    public Budget getByIdForUser(Long id, User user) {
        return budgetRepo.findById(id)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NoSuchElementException("Budget nicht gefunden: " + id));
    }

    @Transactional
    public Budget create(Budget budget, User user) {
        budget.setUser(user);
        return budgetRepo.save(budget);
    }

    @Transactional
    public Budget updateLimit(Long id, Budget updated, User user) {
        Budget existing = getByIdForUser(id, user);
        existing.setLimitAmount(updated.getLimitAmount());
        return budgetRepo.save(existing);
    }

    @Transactional
    public void delete(Long id, User user) {
        Budget existing = getByIdForUser(id, user);
        budgetRepo.delete(existing);
    }

    public List<Map<String, Object>> checkBudgetAlerts(User user) {
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Budget> budgets = budgetRepo.findByUserIdAndMonth(user.getId(), currentMonth);
        List<Transaction> allTransactions = transactionRepo.findByUserId(user.getId());

        List<Map<String, Object>> alerts = new ArrayList<>();

        for (Budget budget : budgets) {
            BigDecimal spent = allTransactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE
                            && t.getCategory() != null
                            && t.getCategory().getId().equals(budget.getCategory().getId())
                            && t.getDate().toString().startsWith(currentMonth))
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (spent.compareTo(budget.getLimitAmount()) > 0) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("category", budget.getCategory().getName());
                alert.put("limit", budget.getLimitAmount());
                alert.put("spent", spent);
                alert.put("exceeded", spent.subtract(budget.getLimitAmount()));
                alerts.add(alert);
            }
        }

        return alerts;
    }
}
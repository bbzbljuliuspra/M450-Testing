package stegmueller.til.centic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TransactionRepository transactionRepo;

    public Map<String, Object> getSummary(User user) {
        BigDecimal totalIncome  = transactionRepo.sumAmountByUserIdAndType(user.getId(), TransactionType.INCOME);
        BigDecimal totalExpense = transactionRepo.sumAmountByUserIdAndType(user.getId(), TransactionType.EXPENSE);
        BigDecimal balance      = totalIncome.subtract(totalExpense);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalIncome",  totalIncome);
        summary.put("totalExpense", totalExpense);
        summary.put("balance",      balance);
        return summary;
    }

    public Map<String, Object> getMonthlySummary(User user, int months) {
        List<Transaction> all = transactionRepo.findByUserId(user.getId());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, BigDecimal> incomeByMonth  = new TreeMap<>();
        Map<String, BigDecimal> expenseByMonth = new TreeMap<>();

        YearMonth current = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            String key = current.minusMonths(i).format(fmt);
            incomeByMonth.put(key, BigDecimal.ZERO);
            expenseByMonth.put(key, BigDecimal.ZERO);
        }

        for (Transaction t : all) {
            String monthKey = t.getDate().format(fmt);
            if (t.getType() == TransactionType.INCOME) {
                incomeByMonth.computeIfPresent(monthKey, (_, v) -> v.add(t.getAmount()));
            } else {
                expenseByMonth.computeIfPresent(monthKey, (_, v) -> v.add(t.getAmount()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("income",  incomeByMonth);
        result.put("expense", expenseByMonth);
        return result;
    }

    public Map<String, BigDecimal> getCategoryStats(User user) {
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        return transactionRepo.findByUserId(user.getId()).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE
                        && t.getCategory() != null
                        && t.getDate().toString().startsWith(currentMonth))
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));
    }
}
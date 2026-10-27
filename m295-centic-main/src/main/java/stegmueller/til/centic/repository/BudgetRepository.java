package stegmueller.til.centic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import stegmueller.til.centic.model.Budget;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserId(Long userId);
    List<Budget> findByUserIdAndMonth(Long userId, String month);
}

package stegmueller.til.centic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepo;

    public List<Transaction> getAllForUser(User user) {
        return transactionRepo.findByUserId(user.getId());
    }

    public Transaction getByIdForUser(Long id, User user) {
        return transactionRepo.findById(id)
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaktion nicht gefunden: " + id));
    }

    @Transactional
    public Transaction create(Transaction transaction, User user) {
        transaction.setUser(user);
        return transactionRepo.save(transaction);
    }

    @Transactional
    public Transaction update(Long id, Transaction updated, User user) {
        Transaction existing = getByIdForUser(id, user);
        existing.setAmount(updated.getAmount());
        existing.setDate(updated.getDate());
        existing.setDescription(updated.getDescription());
        existing.setType(updated.getType());
        existing.setCategory(updated.getCategory());
        return transactionRepo.save(existing);
    }

    @Transactional
    public void delete(Long id, User user) {
        Transaction existing = getByIdForUser(id, user);
        transactionRepo.delete(existing);
    }

    public BigDecimal sumByType(User user, TransactionType type) {
        return transactionRepo.sumAmountByUserIdAndType(user.getId(), type);
    }
}
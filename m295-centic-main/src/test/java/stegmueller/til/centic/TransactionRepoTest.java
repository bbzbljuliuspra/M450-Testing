package stegmueller.til.centic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.annotation.Rollback;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.TransactionRepository;
import stegmueller.til.centic.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@DataJpaTest()
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Rollback(false)
class TransactionRepoTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void insertTransaction() {
        User user = userRepository.save(User.builder()
                .username("testuser")
                .email("test@centic.local")
                .keycloakId("keycloak-111")
                .build());

        Transaction t = transactionRepository.save(Transaction.builder()
                .amount(new BigDecimal("50.00"))
                .date(LocalDate.now())
                .description("Einkauf")
                .type(TransactionType.EXPENSE)
                .user(user)
                .build());

        Assertions.assertNotNull(t.getId());
    }

    @Test
    void findByUserId() {
        User user = userRepository.save(User.builder()
                .username("testuser2")
                .email("test2@centic.local")
                .keycloakId("keycloak-222")
                .build());

        transactionRepository.save(Transaction.builder()
                .amount(new BigDecimal("100.00"))
                .date(LocalDate.now())
                .description("Gehalt")
                .type(TransactionType.INCOME)
                .user(user)
                .build());

        List<Transaction> result = transactionRepository.findByUserId(user.getId());

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(user.getId(), result.getFirst().getUser().getId());
    }

    @Test
    void sumAmountByUserIdAndType() {
        User user = userRepository.save(User.builder()
                .username("testuser3")
                .email("test3@centic.local")
                .keycloakId("keycloak-333")
                .build());

        transactionRepository.save(Transaction.builder()
                .amount(new BigDecimal("200.00"))
                .date(LocalDate.now())
                .description("Gehalt")
                .type(TransactionType.INCOME)
                .user(user)
                .build());

        transactionRepository.save(Transaction.builder()
                .amount(new BigDecimal("50.00"))
                .date(LocalDate.now())
                .description("Miete")
                .type(TransactionType.EXPENSE)
                .user(user)
                .build());

        BigDecimal income  = transactionRepository.sumAmountByUserIdAndType(user.getId(), TransactionType.INCOME);
        BigDecimal expense = transactionRepository.sumAmountByUserIdAndType(user.getId(), TransactionType.EXPENSE);

        Assertions.assertEquals(0, new BigDecimal("200.00").compareTo(income));
        Assertions.assertEquals(0, new BigDecimal("50.00").compareTo(expense));
    }

    @Test
    void findByUserId_emptyForOtherUser() {
        List<Transaction> result = transactionRepository.findByUserId(999L);
        Assertions.assertTrue(result.isEmpty());
    }
}

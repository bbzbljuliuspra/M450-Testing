package stegmueller.til.centic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

// Transaction.java
@Entity
@Table(name = "transactions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @DecimalMin(value = "0.01", message = "Der Betrag muss grösser als 0 sein")
    private BigDecimal amount;

    @NotNull
    private LocalDate date;
    private String description;

    @Enumerated(EnumType.STRING)
    @NotNull
    private TransactionType type;

    @ManyToOne @JoinColumn(name = "user_id")
    @JsonIgnoreProperties("transactions")
    private User user;

    @ManyToOne @JoinColumn(name = "category_id")
    private Category category;
}
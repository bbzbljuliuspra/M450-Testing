package stegmueller.til.centic.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "budgets")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Budget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal limitAmount;

    @Column(name = "`month`")
    private String month;   // Format "2024-05"

    @ManyToOne @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne @JoinColumn(name = "category_id")
    private Category category;
}

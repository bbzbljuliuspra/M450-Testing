package stegmueller.til.centic.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "users")
@Data @NoArgsConstructor
@AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Benutzername darf nicht leer sein")
    @Column(unique = true, nullable = false)
    private String username;

    @Email(message = "Bitte eine gültige E-Mail-Adresse angeben")
    @NotBlank(message = "E-Mail darf nicht leer sein")
    private String email;

    @Column(unique = true, nullable = false)
    @JsonIgnore
    private String keycloakId;
}

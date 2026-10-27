package stegmueller.til.centic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import stegmueller.til.centic.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakId(String keycloakId);
}

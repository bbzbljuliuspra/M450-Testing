package stegmueller.til.centic.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.UserRepository;

@Component
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public CurrentUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        JwtAuthenticationToken auth =
                (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) auth.getCredentials();

        String keycloakId = jwt.getSubject();        // "sub" aus dem JWT
        String email      = jwt.getClaimAsString("email");
        String username   = jwt.getClaimAsString("preferred_username");

        // User laden oder beim ersten Login automatisch anlegen
        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .keycloakId(keycloakId)
                                .email(email)
                                .username(username)
                                .build()
                ));
    }
}

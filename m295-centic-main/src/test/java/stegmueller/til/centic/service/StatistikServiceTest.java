package stegmueller.til.centic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.UserRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatistikService (TDD)")
class StatistikServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StatistikService statistikService;

    @Test
    @DisplayName("Drei User (tim, anna, lukas) -> durchschnittliche Username-Länge 4.0")
    void durchschnittlicheUsernameLaenge_dreiUser_liefertDurchschnitt() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().username("tim").build(),     // 3
                User.builder().username("anna").build(),    // 4
                User.builder().username("lukas").build())); // 5

        // Act
        double durchschnitt = statistikService.durchschnittlicheUsernameLaenge();

        // Assert
        assertEquals(4.0, durchschnitt, 0.0001);
    }
}

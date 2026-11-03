package stegmueller.til.centic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.UserRepository;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * LB1 Teil 4 – TDD: statistischer Wert aus DB-Daten
 * Unit: StatistikService.durchschnittlicheUsernameLaenge()
 * Das UserRepository ist gemockt, die echte Datenbank wird nicht kontaktiert.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatistikService (TDD)")
class StatistikServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StatistikService statistikService;

    private void givenUsernames(String... usernames) {
        List<User> users = Arrays.stream(usernames)
                .map(name -> User.builder().username(name).build())
                .toList();
        when(userRepository.findAll()).thenReturn(users);
    }

    @Test
    @DisplayName("Drei User (tim, anna, lukas) -> durchschnittliche Username-Länge 4.0")
    void durchschnittlicheUsernameLaenge_dreiUser_liefertDurchschnitt() {
        // Arrange
        givenUsernames("tim", "anna", "lukas"); // 3 + 4 + 5 = 12 / 3

        // Act
        double durchschnitt = statistikService.durchschnittlicheUsernameLaenge();

        // Assert
        assertEquals(4.0, durchschnitt, 0.0001);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = ';', value = {
            "til;              3.0",     // ein einziger User: Durchschnitt = seine Länge
            "a;                1.0",     // kürzester möglicher Username
            "tim,anna;         3.5",     // Nachkommastelle: keine Ganzzahl-Division (7 / 2 != 3)
            "a,bb,bb;          1.6667",  // periodischer Wert 5 / 3
            "anna,anna,anna;   4.0",     // alle gleich lang
            "a,abcdefghij;     5.5"      // stark unterschiedliche Längen
    })
    @DisplayName("Durchschnitt wird exakt berechnet")
    void durchschnittWirdExaktBerechnet(String usernames, double erwartet) {
        givenUsernames(usernames.split(","));

        assertEquals(erwartet, statistikService.durchschnittlicheUsernameLaenge(), 0.0001);
    }

    @Test
    @DisplayName("Keine User in der DB -> Durchschnitt 0.0 (nicht NaN)")
    void keineUser_liefertNull() {
        givenUsernames();

        assertEquals(0.0, statistikService.durchschnittlicheUsernameLaenge());
    }

    @Test
    @DisplayName("Liest die User genau einmal aus der DB und sonst nichts")
    void liestUserGenauEinmal() {
        givenUsernames("tim");

        statistikService.durchschnittlicheUsernameLaenge();

        verify(userRepository).findAll();
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("DB-Ausfall wird weitergegeben")
    void dbAusfallWirdWeitergegeben() {
        when(userRepository.findAll()).thenThrow(new DataAccessResourceFailureException("DB nicht erreichbar"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> statistikService.durchschnittlicheUsernameLaenge());
    }
}

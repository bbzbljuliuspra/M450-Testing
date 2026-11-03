package stegmueller.til.centic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import stegmueller.til.centic.model.User;
import stegmueller.til.centic.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatistikService {

    private final UserRepository userRepository;

    public double durchschnittlicheUsernameLaenge() {
        List<User> users = userRepository.findAll();
        int summe = 0;
        for (User user : users) {
            summe += user.getUsername().length();
        }
        return (double) summe / users.size();
    }
}

package stegmueller.til.centic.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import stegmueller.til.centic.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class StatistikService {

    private final UserRepository userRepository;

    public double durchschnittlicheUsernameLaenge() {
        return 0.0;
    }
}

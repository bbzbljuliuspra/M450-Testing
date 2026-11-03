package stegmueller.til.centic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Zentrale Uhr, damit zeitabhängige Logik in Tests mit Clock.fixed(...) eingefroren werden kann
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

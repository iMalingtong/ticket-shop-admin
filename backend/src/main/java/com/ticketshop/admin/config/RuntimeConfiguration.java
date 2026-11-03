package com.ticketshop.admin.config;

import java.time.Clock;
import java.util.Arrays;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RuntimeConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    InitializingBean explicitEnvironmentRequired(Environment environment) {
        return () -> {
            String[] profiles = environment.getActiveProfiles();
            long count = Arrays.stream(profiles)
                    .filter(profile -> Arrays.asList("development", "test", "production").contains(profile))
                    .count();
            if (count != 1) {
                throw new IllegalStateException("Exactly one runtime profile is required: development, test, or production");
            }
        };
    }
}

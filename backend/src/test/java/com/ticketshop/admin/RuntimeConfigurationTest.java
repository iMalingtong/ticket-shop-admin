package com.ticketshop.admin;

import com.ticketshop.admin.config.RuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeConfiguration.class);

    @Test
    void refusesMissingOrAmbiguousRuntimeProfile() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.profiles.active=development,production")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void providesUtcClockOnlyWithExplicitProfile() {
        runner.withPropertyValues("spring.profiles.active=development")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(java.time.Clock.class).getZone().getId()).isEqualTo("Z");
                });
    }
}

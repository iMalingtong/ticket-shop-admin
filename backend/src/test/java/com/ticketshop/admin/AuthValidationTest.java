package com.ticketshop.admin;

import com.ticketshop.admin.authentication.api.AuthController;
import com.ticketshop.admin.authentication.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class AuthValidationTest {
    @Test void configuredMinimumVersionIsComparedNumericallyAndProtocolRemainsMandatory() {
        var env=new MockEnvironment();env.setActiveProfiles("development");
        var controller=new AuthController(null,Clock.systemUTC(),env,"http://127.0.0.1:8848","1.2.3");
        assertThatCode(()->ReflectionTestUtils.invokeMethod(controller,"metadata","1.10.0",1)).doesNotThrowAnyException();
        assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(controller,"metadata","1.2.2",1)).isInstanceOf(AuthProblem.class).hasMessage("CLIENT_UPDATE_REQUIRED");
        assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(controller,"metadata","unknown",1)).isInstanceOf(AuthProblem.class).hasMessage("CLIENT_UPDATE_REQUIRED");
        assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(controller,"metadata","1.10.0",2)).isInstanceOf(AuthProblem.class).hasMessage("PROTOCOL_UNSUPPORTED");
        assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(controller,"metadata","1.10.0",0)).isInstanceOf(AuthProblem.class).hasMessage("PROTOCOL_INVALID");
    }
    @Test void productionRequiresHttpsOrigins() {
        var env=new MockEnvironment();env.setActiveProfiles("production");
        assertThatThrownBy(()->new AuthController(null,Clock.systemUTC(),env,"http://127.0.0.1:8848","")).isInstanceOf(IllegalStateException.class);
        assertThatCode(()->new AuthController(null,Clock.systemUTC(),env,"https://admin.example.test","")).doesNotThrowAnyException();
    }
    @Test void argon2DevelopmentCostIsMeasuredWithoutReducingParameters() {
        var passwords=new Credentials();String password="SyntheticBenchmarkPassword-2026",hash=passwords.encode(password);long start=System.nanoTime();
        for(int i=0;i<10;i++) assertThat(passwords.matches(password,hash)).isTrue();
        System.out.printf("Argon2id 19456KiB/t=2/p=1, 10 verifies mean=%.1fms%n",(System.nanoTime()-start)/10_000_000.0);
    }
}

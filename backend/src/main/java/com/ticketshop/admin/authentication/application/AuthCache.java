package com.ticketshop.admin.authentication.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@Component
public class AuthCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String prefix;
    private final Clock clock;
    public AuthCache(StringRedisTemplate redis,ObjectMapper json,Environment environment,Clock clock) { this.redis=redis; this.json=json; this.prefix="ticket-shop-admin:"+Arrays.stream(environment.getActiveProfiles()).filter(p -> Set.of("test","development","production").contains(p)).findFirst().orElseThrow()+":"; this.clock=clock; }
    public void read(String suffix) { try { RequestBudget.redis(); redis.opsForValue().get(prefix+suffix); } catch(Exception e) { throw new AuthProblem(SERVICE_UNAVAILABLE); } }
    public void write(String suffix,Map<String,Object> value,Instant until) {
        long seconds=Duration.between(clock.instant(),until).getSeconds(); if(seconds<=0) return;
        try { RequestBudget.redis(); redis.opsForValue().set(prefix+suffix,json.writeValueAsString(value),Duration.ofSeconds(seconds)); }
        catch(Exception e) { throw new AuthProblem(SERVICE_UNAVAILABLE); }
    }
    public void delete(String suffix) { try { RequestBudget.redis(); redis.delete(prefix+suffix); } catch(Exception e) { throw new AuthProblem(SERVICE_UNAVAILABLE); } }
}

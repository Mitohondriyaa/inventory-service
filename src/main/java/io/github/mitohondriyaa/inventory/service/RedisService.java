package io.github.mitohondriyaa.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {
    private final StringRedisTemplate stringRedisTemplate;
    @Value("${message-id.ttl.minutes}")
    private Integer messageIdTtlMinutes;

    public Boolean setValue(String key) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "", Duration.ofMinutes(messageIdTtlMinutes));
    }
}
package io.github.mitohondriyaa.inventory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@TestConfiguration
public class TestRedisConfig {
    @Value("${redis.port}")
    private Integer port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", port);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
        RedisConnectionFactory redisCounterConnectionFactory
    ) {
        return new StringRedisTemplate(redisCounterConnectionFactory);
    }
}
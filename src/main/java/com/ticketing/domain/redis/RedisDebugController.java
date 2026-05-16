package com.ticketing.domain.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class RedisDebugController {

    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping("/redis/keys")
    public Set<String> keys(@RequestParam(defaultValue = "*") String pattern) {
        return redisTemplate.keys(pattern);
    }

    @GetMapping("/redis/get")
    public String get(@RequestParam String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @GetMapping("/redis/find-seat-id")
    public Map<String, String> findSeatId(@RequestParam Long seatId) {
        Map<String, String> result = new HashMap<>();
        Set<String> keys = redisTemplate.keys("seats:available:*");
        if (keys == null) return result;
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && value.contains("\"id\":" + seatId)) {
                result.put(key, value);
            }
        }
        return result;
    }

}

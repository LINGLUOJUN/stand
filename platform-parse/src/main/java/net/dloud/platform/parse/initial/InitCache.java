package net.dloud.platform.parse.initial;

import lombok.extern.slf4j.Slf4j;
import net.dloud.platform.extend.assist.serialization.KeySerializer;
import net.dloud.platform.extend.assist.serialization.KryoSerializer;
import net.dloud.platform.extend.assist.serialization.LongSerializer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author QuDasheng
 * @create 2018-10-22 14:54
 **/
@Slf4j
@EnableCaching
@Configuration
@ConditionalOnProperty(name = "source.init.enable", matchIfMissing = true, havingValue = "true")
@AutoConfigureAfter(InitDubbo.class)
public class InitCache {
    /**
     * redis操作
     */
    @Bean
    @Primary
    public RedisTemplate<String, ?> redisTemplate(RedisConnectionFactory redisFactory) {
        final RedisTemplate<String, ?> template = new RedisTemplate<>();
        template.setConnectionFactory(redisFactory);

        final KeySerializer keySerializer = new KeySerializer();
        final RedisSerializer<?> stringSerializer = new StringRedisSerializer();
        final KryoSerializer kryoSerializer = new KryoSerializer();

        // key序列化
        template.setKeySerializer(keySerializer);
        // value序列化
        template.setValueSerializer(kryoSerializer);
        // hash key序列化
        template.setHashKeySerializer(stringSerializer);
        // hash value序列化
        template.setHashValueSerializer(kryoSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean("stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisFactory) {
        final StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisFactory);

        final KeySerializer keySerializer = new KeySerializer();
        final RedisSerializer<?> stringSerializer = new StringRedisSerializer();

        // key序列化
        template.setKeySerializer(keySerializer);
        // value序列化
        template.setValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean("longRedisTemplate")
    public RedisTemplate<String, Long> longRedisTemplate(RedisConnectionFactory redisFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(redisFactory);

        final KeySerializer keySerializer = new KeySerializer();
        final LongSerializer longSerializer = new LongSerializer();

        // key序列化
        template.setKeySerializer(keySerializer);
        // value序列化
        template.setValueSerializer(longSerializer);
        template.afterPropertiesSet();
        return template;
    }
}

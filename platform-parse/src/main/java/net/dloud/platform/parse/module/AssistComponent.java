package net.dloud.platform.parse.module;

import lombok.extern.slf4j.Slf4j;
import net.dloud.platform.extend.constant.CenterEnum;
import net.dloud.platform.extend.constant.PlatformConstants;
import net.dloud.platform.parse.redisson.dataccess.WrapperLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author QuDasheng
 * @create 2018-10-07 12:37
 **/
@Slf4j
@Component
@ConditionalOnProperty(name = "assist.init.enable", matchIfMissing = true, havingValue = "true")
public class AssistComponent {
    private final RedissonClient redis;
    private final ConcurrentMap<String, RTopic<Object>> topics = new ConcurrentHashMap<>();


    @Autowired
    public AssistComponent(RedissonClient redis) {
        this.redis = redis;
    }

    /**
     * 竞争锁
     *
     * @param key
     * @return
     */
    public WrapperLock getLock(Object key) {
        return new WrapperLock(redis.getLock(PlatformConstants.APPID + "-" + key));
    }

    /**
     * 公平锁
     *
     * @param key
     * @return
     */
    public WrapperLock getFairLock(Object key) {
        return new WrapperLock(redis.getFairLock(PlatformConstants.APPID + "-" + key));
    }

    /**
     * 向系统发送消息
     * 没有持久化，没有分组
     *
     * @param message
     * @return
     */
    public long publish(Object message) {
        return publish(PlatformConstants.APPID + "-" + PlatformConstants.MODE + "-" + message.getClass().getTypeName(), message);
    }

    public long publish(CenterEnum center, Object message) {
        return publish(center.getTopic() + "-" + message.getClass().getTypeName(), message);
    }

    public long publish(String name, Object message) {
        return getTopic(name).publish(message);
    }

    /**
     * 监听消息
     * 同上没有持久化，没有分组
     *
     * @param listener
     * @param clazz    要接受的消息的具体类型
     * @return
     */
    public int listener(MessageListener<Object> listener, Class<?> clazz) {
        return listener(PlatformConstants.APPID + "-" + PlatformConstants.MODE + "-" + clazz.getTypeName(), listener);
    }

    public int listener(CenterEnum center, MessageListener<Object> listener, Class<?> clazz) {
        return listener(center.getTopic() + "-" + clazz.getTypeName(), listener);
    }

    public int listener(String name, MessageListener<Object> listener) {
        return getTopic(name).addListener(listener);
    }

    private RTopic<Object> getTopic(String name) {
        RTopic<Object> topic = topics.get(name);
        if (Objects.isNull(topic)) {
            topic = redis.getTopic(name);
            topics.put(name, topic);
        }
        return topic;
    }
}

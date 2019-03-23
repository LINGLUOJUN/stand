package net.dloud.platform.parse.module;

import lombok.extern.slf4j.Slf4j;
import net.dloud.platform.common.extend.RandomUtil;
import net.dloud.platform.extend.assist.SequenceEnum;
import net.dloud.platform.extend.assist.dataccess.SequenceRecord;
import net.dloud.platform.extend.constant.PlatformConstants;
import net.dloud.platform.extend.constant.PlatformExceptionEnum;
import net.dloud.platform.extend.constant.StartupConstants;
import net.dloud.platform.extend.exception.PassedException;
import org.jdbi.v3.core.Jdbi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author QuDasheng
 * @create 2018-09-27 16:47
 **/
@Slf4j
@Component
@ConditionalOnProperty(name = "source.init.enable", matchIfMissing = true, havingValue = "true")
public class SequenceComponent {
    private long start = 20_000_000L;
    private long multi = 10_000_000_000L;

    private Map<String, RedisAtomicLong> reds = new ConcurrentHashMap<>();
    private Map<String, SequenceRecord> sets = new ConcurrentHashMap<>();
    private Map<String, AtomicLong> seqs = new ConcurrentHashMap<>();

    private final Jdbi jdbi;
    private final RedisTemplate<String, Long> redis;

    @Value("${seq.type:1}")
    private Integer type;


    @Autowired
    public SequenceComponent(@Qualifier("longRedisTemplate") RedisTemplate<String, Long> redisTemplate,
                             @Qualifier("pubDataSource") DataSource dataSource) {
        this.jdbi = Jdbi.create(dataSource);
        this.redis = redisTemplate;
    }

    /**
     * 根据key生成id
     */
    public Long getSequence(final SequenceEnum sequence) {
        switch (type) {
            case 2:
                return seqByRedis(sequence);
            default:
                return seqByDatabase(sequence);
        }
    }

    public Long seqByRedis(final SequenceEnum sequence) {
        final String keyName = sequence.getKey();
        final RedisAtomicLong value = reds.get(keyName);

        long id;
        if (null == value) {
            final RedisAtomicLong atomic = new RedisAtomicLong(keyName, redis);
            id = atomic.incrementAndGet();
            reds.put(keyName, atomic);
        } else {
            id = value.incrementAndGet();
        }

        log.debug("[PLATFORM] 生成用于 {} 的ID: {}", sequence.getDescribe(), id);
        return id;
    }

    private Long seqByDatabase(final SequenceEnum sequence) {
        final String keyName = PlatformConstants.APPID + "-" + StartupConstants.RUN_MODE + "-" + sequence.getKey();
        final AtomicLong value = seqs.get(keyName);
        final SequenceRecord record = sets.get(keyName);

        long id;
        if (null == value) {
            id = useRecord(keyName, start);
        } else {
            final long result = value.get();
            final long limit = record.getFinish();
            if (result + 10 >= limit) {
                id = useRecord(keyName, limit);
            } else {
                id = incType(value, record);
            }
        }

        log.debug("[PLATFORM] 生成用于 {} 的ID: {}", sequence.getDescribe(), id);
        return id;
    }

    private long incType(final AtomicLong value, final SequenceRecord record) {
        Byte step = record.getStep();
        if (null == step || record.getRefresh() < 10_000) {
            step = 10;
        }
        switch (record.getIncType()) {
            case 0:
                return value.addAndGet(1);
            case 2:
                final Long assist = record.getAssist();
                if (null == assist || assist <= 0) {
                    return value.addAndGet(RandomUtil.getRandomInt(1, step)) % multi;
                } else {
                    return value.addAndGet(RandomUtil.getRandomInt(1, step)) % assist;
                }
            default:
                return value.addAndGet(RandomUtil.getRandomInt(1, step));
        }
    }

    private long useRecord(final String keyName, final Long limit) {
        synchronized (keyName.intern()) {
            final AtomicLong getVal = seqs.get(keyName);
            final SequenceRecord getRec = sets.get(keyName);
            if (null != getVal && null != getRec && getRec.getFinish() > limit) {
                return incType(getVal, getRec);
            }

            return jdbi.inTransaction(handle -> {
                Optional<SequenceRecord> optional = handle.createQuery(String.format(getSequenceRecord, keyName))
                        .mapToBean(SequenceRecord.class).findFirst();

                AtomicLong value;
                SequenceRecord record;
                if (optional.isPresent()) {
                    record = optional.get();
                    Long start = record.getFinish();
                    Long finish = start + record.getRefresh();
                    record.setStart(start);
                    record.setFinish(finish);
                    value = new AtomicLong(start);
                    log.info("[PLATFORM] 当前序列[{}]重新从数据库里获取, 起始值[{}]", keyName, start);
                    final int execute = handle.createUpdate(String.format(updateSequenceRecord,
                            record.getStart(), record.getFinish(), keyName)).execute();
                    if (execute == 0) {
                        throw new PassedException(PlatformExceptionEnum.GENERATION_SEQUENCE);
                    }
                } else {
                    record = new SequenceRecord(limit);
                    value = new AtomicLong(limit);
                    final int execute = handle.createUpdate(String.format(insertSequenceRecord,
                            keyName, record.getStart(), record.getFinish())).execute();
                    if (execute == 0) {
                        throw new PassedException(PlatformExceptionEnum.GENERATION_SEQUENCE);
                    }
                }

                seqs.put(keyName, value);
                sets.put(keyName, record);
                return incType(value, record);
            });
        }
    }

    private String getSequenceRecord = "SELECT * FROM SEQUENCE_GENERATE_RECORD WHERE produce = '%s' FOR UPDATE";
    private String insertSequenceRecord = "INSERT INTO SEQUENCE_GENERATE_RECORD (produce, start, finish) VALUES ('%s', %d, %d)";
    private String updateSequenceRecord = "UPDATE SEQUENCE_GENERATE_RECORD SET start = %d, finish = %d WHERE produce = '%s'";
}

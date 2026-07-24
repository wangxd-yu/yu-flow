package org.yu.flow.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import com.highgo.jdbc.util.PGobject;
import org.yu.flow.serializer.PGObjectSerializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 流程/API 层统一 ObjectMapper 工厂。
 *
 * <p>返回<strong>懒加载单例</strong>（配置只读，读写线程安全）。DSL 引擎解析请继续使用专用
 * {@code new ObjectMapper()}，避免与日期/PG 序列化配置互相干扰。</p>
 */
public final class FlowObjectMapperUtil {

    private static final class Holder {
        private static final ObjectMapper INSTANCE = create();
    }

    private FlowObjectMapperUtil() {
    }

    /** 共享配置好的 ObjectMapper；调用方勿再 registerModule / configure。 */
    public static ObjectMapper flowObjectMapper() {
        return Holder.INSTANCE;
    }

    private static ObjectMapper create() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(PGobject.class, new PGObjectSerializer());
        objectMapper.registerModule(module);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
                .toFormatter();

        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer(formatter));
        javaTimeModule.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(formatter.format(value));
            }
        });

        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true);
        return objectMapper;
    }
}

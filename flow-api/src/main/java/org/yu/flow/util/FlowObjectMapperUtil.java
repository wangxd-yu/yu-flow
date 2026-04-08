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
 * @author yu-flow
 * @date 2025-03-13 10:30
 */
public class FlowObjectMapperUtil {

    public static ObjectMapper flowObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(PGobject.class, new PGObjectSerializer());
        objectMapper.registerModule(module);

        // 注册 JavaTimeModule 并配置日期格式
        // 配置日期时间格式
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 自定义日期时间序列化格式
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true) // 可选毫秒部分
                .toFormatter();

        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer(formatter));
        javaTimeModule.addSerializer(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(formatter.format(value));
            }
        });

        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 禁用时间戳格式
        objectMapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true); // 使用时区
        return objectMapper;
    }

   /* @Bean
    @Primary
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper); // 使用自定义的 ObjectMapper
    }*/
}

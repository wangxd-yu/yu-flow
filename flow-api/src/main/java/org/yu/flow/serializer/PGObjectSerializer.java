package org.yu.flow.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.highgo.jdbc.util.PGobject;

import java.io.IOException;

public class PGObjectSerializer extends JsonSerializer<PGobject> {

    @Override
    public void serialize(PGobject value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            gen.writeRawValue(value.getValue()); // 只写入内容
        } else {
            gen.writeNull();
        }
    }
}

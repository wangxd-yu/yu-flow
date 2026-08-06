package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.yu.flow.module.oss.annotation.FlowOssFileUrl;
import org.yu.flow.module.oss.service.OssObjectService;

import java.io.IOException;

/**
 * Jackson 序列化处理器：响应 JSON 时自动将 fileId 替换为访问 URL 字符串
 */
public class FlowOssFileUrlSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private String profile;
    private boolean absolute = false;

    public FlowOssFileUrlSerializer() {
    }

    public FlowOssFileUrlSerializer(String profile, boolean absolute) {
        this.profile = profile;
        this.absolute = absolute;
    }

    @Override
    public void serialize(String fileId, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (StrUtil.isBlank(fileId)) {
            gen.writeNull();
            return;
        }

        try {
            OssObjectService ossObjectService = SpringUtil.getBean(OssObjectService.class);
            String resolvedUrl = ossObjectService.resolveAccessUrl(fileId.trim(), this.profile, this.absolute);
            if (resolvedUrl != null) {
                gen.writeString(resolvedUrl);
            } else {
                gen.writeString(fileId);
            }
        } catch (Exception e) {
            gen.writeString(fileId);
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            FlowOssFileUrl ann = property.getAnnotation(FlowOssFileUrl.class);
            if (ann != null) {
                return new FlowOssFileUrlSerializer(ann.profile(), ann.absolute());
            }
        }
        return this;
    }
}

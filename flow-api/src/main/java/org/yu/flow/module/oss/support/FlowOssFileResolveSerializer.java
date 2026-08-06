package org.yu.flow.module.oss.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.yu.flow.module.oss.annotation.FlowOssFileResolve;
import org.yu.flow.module.oss.dto.FlowOssFileResolvedDTO;
import org.yu.flow.module.oss.service.OssObjectService;

import java.io.IOException;

/**
 * Jackson 序列化处理器：响应 JSON 时自动将 fileId 替换为 FlowOssFileResolvedDTO 详细元信息对象
 */
public class FlowOssFileResolveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private String profile;
    private boolean absolute = false;

    public FlowOssFileResolveSerializer() {
    }

    public FlowOssFileResolveSerializer(String profile, boolean absolute) {
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
            FlowOssFileResolvedDTO detail = ossObjectService.resolveFileDetail(fileId.trim(), this.profile, this.absolute);
            if (detail != null) {
                gen.writeObject(detail);
            } else {
                gen.writeNull();
            }
        } catch (Exception e) {
            gen.writeNull();
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            FlowOssFileResolve ann = property.getAnnotation(FlowOssFileResolve.class);
            if (ann != null) {
                return new FlowOssFileResolveSerializer(ann.profile(), ann.absolute());
            }
        }
        return this;
    }
}

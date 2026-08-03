package org.yu.flow.engine.model.step;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yu.flow.engine.model.NodeType;
import org.yu.flow.engine.model.PortDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.Step;

import java.util.Arrays;
import java.util.List;

/**
 * OSS 对象存储节点：经 {@code flow_oss_connection} 对 MinIO/S3 兼容存储执行 put/get/delete/list/presignGet。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OssStep extends Step {

    /** put | get | delete | list | presignGet */
    private String operation;

    /** OSS 连接编码（flow_oss_connection.code） */
    private String connectionCode;

    /** 桶名（可空，get/delete/list/presign 必填或由连接默认桶推断） */
    private String bucket;

    /** 对象键，支持 ${var} */
    private String objectKey;

    /** put 时 Content-Type */
    private String contentType;

    /** put 时从上下文变量读取字节（byte[] / Base64 字符串 / 普通字符串） */
    private String localBytesVar;

    /** list 前缀 */
    private String listPrefix;

    /** list 最大条数，默认 100 */
    private Integer listMaxKeys;

    /** presignGet 有效期（秒） */
    private Integer presignExpireSeconds;

    @Override
    public String getType() {
        return NodeType.OSS;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(
                PortDefinition.output(PortNames.SUCCESS),
                PortDefinition.output(PortNames.FAIL)
        );
    }
}

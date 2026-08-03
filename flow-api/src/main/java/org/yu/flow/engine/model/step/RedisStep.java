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
 * Redis 读写节点：get / set / del / incr。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RedisStep extends Step {

    /** get | set | del | incr */
    private String operation = "get";

    /** Redis key，支持 ${var} */
    private String key;

    /** set 时的值，支持 ${var} */
    private String value;

    /** set 时可选 TTL（秒） */
    private Long ttlSeconds;

    @Override
    public String getType() {
        return NodeType.REDIS;
    }

    @Override
    public List<PortDefinition> getOutputPorts() {
        return Arrays.asList(PortDefinition.output(PortNames.OUT));
    }
}

package org.yu.flow.module.mq.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.mq.domain.MqConnectionDO;
import org.yu.flow.module.mq.dto.MqConnectionDTO;
import org.yu.flow.module.mq.provider.MqConnectionSpec;
import org.yu.flow.module.mq.query.MqConnectionQueryDTO;

import java.util.List;

/**
 * MQ 连接配置业务服务
 *
 * @author yu-flow
 */
public interface MqConnectionService {

    /** 新建连接（密码 AES 加密后落库） */
    MqConnectionDO save(MqConnectionDO connectionDO);

    /** 更新连接（密码留空表示保持原密码；变更后失效 Provider 客户端缓存） */
    MqConnectionDO update(MqConnectionDO connectionDO);

    /** 删除连接 */
    void delete(String id);

    /** 批量删除 */
    void batchDelete(List<String> ids);

    /** 按 ID 查询 */
    MqConnectionDO findById(String id);

    /** 分页查询 */
    PageBean<MqConnectionDTO> findPage(MqConnectionQueryDTO queryDTO);

    /** 全部启用的连接（节点下拉选项） */
    List<MqConnectionDTO> listEnabled();

    /** 启用 */
    MqConnectionDO enable(String id);

    /** 停用 */
    MqConnectionDO disable(String id);

    /**
     * 测试连接（走 Provider SPI）。
     *
     * <p>probe 携带表单参数；密码留空且 id 有值时回填库中密码。
     * 若 id 有值，测试结果（健康状态/错误信息/测试时间）回写该记录。</p>
     *
     * @throws org.yu.flow.exception.FlowException 连接失败时抛出
     */
    void testConnection(MqConnectionDO probe);

    /**
     * 按连接编码列出 topic 候选（表单下拉提示）。
     *
     * <p>不支持枚举的中间件（如 RabbitMQ）返回空列表；连接异常时抛出。</p>
     */
    List<String> listTopics(String connectionCode, String keyword, int limit);

    /**
     * 按编码构造 Provider 连接规格（密码已解密）。
     * 供 MqSendService / MqConsumerManager 使用。
     *
     * @throws org.yu.flow.exception.FlowException 连接不存在或已停用
     */
    MqConnectionSpec buildSpec(String code);
}

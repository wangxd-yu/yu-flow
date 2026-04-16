package org.yu.flow.module.responsetemplate.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * API 响应模板实体
 *
 * <p>用于管理不同业务线、不同调用方（App 端、小程序端、第三方 ERP 等）
 * 对 API 返回 JSON 格式的差异化包装需求。每个模板定义了成功/失败两种包装体格式。</p>
 *
 * <h3>核心占位符</h3>
 * <ul>
 *   <li>{@code @Result} — 运行时替换为实际业务数据</li>
 *   <li>{@code @Error} — 运行时替换为异常信息</li>
 * </ul>
 *
 * <h3>全局默认机制</h3>
 * <p>系统中有且仅有一条 {@code isDefault = 1} 的模板记录，作为未显式指定模板的 API 的默认包装格式。
 * 当新模板被设为默认时，原默认模板自动取消。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_response_template")
public class ResponseTemplateDO {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /**
     * 模板名称（如：标准App响应、ERP对接格式）
     */
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    /**
     * 成功包装体 JSON 模板
     * <p>示例: {"code": 200, "message": "success", "data": @Result}</p>
     */
    @Column(name = "success_wrapper", columnDefinition = "TEXT")
    private String successWrapper;

    /**
     * 分页包装体 JSON 模板
     */
    @Column(name = "page_wrapper", columnDefinition = "TEXT")
    private String pageWrapper;

    /**
     * 失败包装体 JSON 模板
     * <p>示例: {"code": 500, "message": @Error, "data": null}</p>
     */
    @Column(name = "fail_wrapper", columnDefinition = "TEXT")
    private String failWrapper;

    /**
     * 是否为全局默认模板 (1: 默认, 0: 非默认)
     * <p>系统中有且仅有一条默认记录</p>
     */
    @Column(name = "is_default", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Integer isDefault;

    /**
     * 备注说明
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建者 */
    @Column(name = "create_by", length = 64)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @Column(name = "update_by", length = 64)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}

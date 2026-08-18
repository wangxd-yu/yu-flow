package org.yu.flow.module.directory.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 全局目录实体
 *
 * @author yu-flow
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_directory")
public class FlowDirectoryDO {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 父节点ID，NULL 表示根节点 */
    private String parentId;

    /** 目录名称 */
    @Column(nullable = false)
    private String name;

    /**
     * 业务域：api / task / service / model / page / mqtask；
     * 空表示各模块共用（兼容历史数据）。
     */
    @Column(length = 32)
    private String bizType;

    /** 排序（升序） */
    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer sort;

    /**
     * URL 路径前缀（可选）。如 {@code /api/v1}。
     * <p>仅作编辑期默认值与分组约定；运行时以接口自身 path 为准。</p>
     */
    @Column(length = 256)
    private String pathPrefix;

    /**
     * 目录级入站防护 JSON（结构同接口 {@code ApiSecurityConfig}）。
     * <p>{@code null}/空白 = 本目录不覆盖，继续向上继承；字段级 null = 继承上级。</p>
     */
    @Column(columnDefinition = "TEXT")
    private String securityConfig;

    /**
     * 目录级出站隐私 JSON（结构同 {@code ApiPrivacyConfig}）。
     */
    @Column(columnDefinition = "TEXT")
    private String privacyConfig;

    /** 备注 */
    @Column(length = 512)
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}

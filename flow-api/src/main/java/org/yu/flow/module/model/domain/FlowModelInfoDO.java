package org.yu.flow.module.model.domain;

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
 * 数据模型信息实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_model_info")
public class FlowModelInfoDO {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 关联全局目录树 */
    private String directoryId;

    /** 模型中文名，如：用户信息 */
    @Column(nullable = false)
    private String name;

    /** 底层物理表名，如：t_user */
    @Column(name = "table_name", nullable = false, unique = true)
    private String tableName;

    /** 关联的动态数据源 code */
    @Column(name = "datasource", length = 50)
    private String datasource;

    /** 核心元数据 JSON 数组（包含字段名、类型、UI配置等），大文本字段 */
    @Column(name = "fields_schema", columnDefinition = "LONGTEXT")
    @Lob
    private String fieldsSchema;

    /** 状态：0=停用，1=启用 */
    @Column(columnDefinition = "TINYINT DEFAULT 0")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}

package org.yu.flow.module.page.domain;

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
 * 页面信息实体
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
@Table(name = "flow_page_info")
public class PageInfoDO {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    /** 关联全局目录树 */
    private String directoryId;

    /** 页面名称 */
    @Column(nullable = false)
    private String name;

    /** 访问路径（唯一） */
    @Column(name = "route_path", nullable = false, unique = true)
    private String routePath;

    /** 页面配置 JSON Schema（Amis），大文本字段 */
    @Column(columnDefinition = "LONGTEXT")
    @Lob
    private String json;

    /** 状态：0=草稿，1=已发布 */
    @Column(columnDefinition = "TINYINT DEFAULT 0")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}

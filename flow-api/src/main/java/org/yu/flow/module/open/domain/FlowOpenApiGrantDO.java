package org.yu.flow.module.open.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_open_api_grant")
public class FlowOpenApiGrantDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "platform_id", nullable = false, length = 32)
    private String platformId;

    @Column(name = "api_id", nullable = false, length = 32)
    private String apiId;

    @Column(name = "allow_methods", length = 64)
    private String allowMethods;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private Date createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = new Date();
    }
}

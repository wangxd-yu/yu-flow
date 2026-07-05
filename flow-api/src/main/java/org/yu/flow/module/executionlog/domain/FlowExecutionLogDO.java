package org.yu.flow.module.executionlog.domain;

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
@Table(name = "flow_execution_log")
public class FlowExecutionLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    private String apiId;
    private String apiName;
    private String url;
    /** 接口类型：FLOW / DB / JSON / STRING */
    private String serviceType;
    private String method;

    @Column(columnDefinition = "LONGTEXT")
    private String requestParams;

    @Column(columnDefinition = "LONGTEXT")
    private String responseBody;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    private Long costTimeMs;

    @Column(columnDefinition = "LONGTEXT")
    private String traceData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = new Date();
        }
    }
}

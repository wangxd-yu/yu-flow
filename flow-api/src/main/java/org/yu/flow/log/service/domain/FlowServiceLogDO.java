package org.yu.flow.log.service.domain;

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
@Table(name = "flow_log_service")
public class FlowServiceLogDO {

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(nullable = false, length = 32)
    private String serviceId;

    private String serviceName;

    /** MANUAL / CALL / DEBUG */
    @Column(length = 16)
    private String triggerType;

    @Column(length = 16)
    private String status;

    private Long costTimeMs;

    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    @Basic(fetch = FetchType.LAZY)
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

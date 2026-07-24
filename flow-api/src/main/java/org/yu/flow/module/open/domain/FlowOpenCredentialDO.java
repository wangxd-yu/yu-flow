package org.yu.flow.module.open.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "flow_open_credential")
public class FlowOpenCredentialDO {

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_ROTATED = 2;

    @Id
    @GeneratedValue(generator = "snow_id")
    @GenericGenerator(name = "snow_id", strategy = "org.yu.flow.auto.util.SnowIdGenerator")
    private String id;

    @Column(name = "platform_id", nullable = false, length = 32)
    private String platformId;

    @Column(name = "app_key", nullable = false, length = 64)
    private String appKey;

    @Column(name = "app_secret_enc", nullable = false, length = 512)
    private String appSecretEnc;

    @Column(name = "secret_hint", length = 16)
    private String secretHint;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "rotated_from_id", length = 32)
    private String rotatedFromId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (status == null) status = STATUS_ENABLED;
    }
}

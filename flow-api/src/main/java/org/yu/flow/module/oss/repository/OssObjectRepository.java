package org.yu.flow.module.oss.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.oss.domain.OssObjectDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OssObjectRepository extends JpaRepository<OssObjectDO, String>,
        JpaSpecificationExecutor<OssObjectDO> {

    Optional<OssObjectDO> findByIdAndStatus(String id, String status);

    List<OssObjectDO> findTop100ByStatusAndObjectPurged(String status, Boolean objectPurged);

    List<OssObjectDO> findTop100ByStatusAndExpiresAtBefore(String status, LocalDateTime expiresAt);

    List<OssObjectDO> findByProfileCodeAndStatus(String profileCode, String status);

    @Query("SELECT COALESCE(SUM(o.sizeBytes), 0) FROM OssObjectDO o "
            + "WHERE o.profileCode = :profileCode AND o.status = :status")
    long sumSizeBytesByProfileCodeAndStatus(@Param("profileCode") String profileCode,
                                            @Param("status") String status);

    @Query("SELECT COUNT(o) FROM OssObjectDO o WHERE o.profileCode = :profileCode AND o.status = :status")
    long countByProfileCodeAndStatus(@Param("profileCode") String profileCode, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(o.sizeBytes), 0) FROM OssObjectDO o "
            + "WHERE o.uploadedBy = :uploadedBy AND o.status = :status")
    long sumSizeBytesByUploadedByAndStatus(@Param("uploadedBy") String uploadedBy,
                                           @Param("status") String status);

    @Query("SELECT COUNT(o) FROM OssObjectDO o WHERE o.uploadedBy = :uploadedBy AND o.status = :status")
    long countByUploadedByAndStatus(@Param("uploadedBy") String uploadedBy, @Param("status") String status);
}

package org.yu.flow.module.release.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.yu.flow.module.release.domain.FlowReleaseImportLogDO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlowReleaseImportLogRepository extends JpaRepository<FlowReleaseImportLogDO, String>,
        JpaSpecificationExecutor<FlowReleaseImportLogDO> {

    Optional<FlowReleaseImportLogDO> findFirstByPackageDigestAndStatusOrderByImportedTimeDesc(String digest, String status);

    /** 是否有更晚的成功导入（回滚只允许最近一次） */
    boolean existsByStatusAndImportedTimeAfter(String status, LocalDateTime time);

    /** 仍保留备份的记录 ID，最近的在前（只取 ID，备份本身可能很大） */
    @Query("select l.id from FlowReleaseImportLogDO l where l.backupJson is not null order by l.importedTime desc")
    List<String> findIdsWithBackup();

    @Modifying
    @Query("update FlowReleaseImportLogDO l set l.backupJson = null where l.id in :ids")
    int clearBackups(@Param("ids") Collection<String> ids);
}

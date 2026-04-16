package org.yu.flow.module.responsetemplate.repository;

import org.yu.flow.module.responsetemplate.domain.ResponseTemplateDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * API 响应模板 Repository
 */
public interface ResponseTemplateRepository extends JpaRepository<ResponseTemplateDO, String>,
        JpaSpecificationExecutor<ResponseTemplateDO> {

    /**
     * 判断模板名称是否已存在（新增时唯一性校验）
     */
    boolean existsByTemplateName(String templateName);

    /**
     * 判断模板名称是否已被其他记录占用（更新时排除自身）
     */
    boolean existsByTemplateNameAndIdNot(String templateName, String id);

    /**
     * 查询全局默认模板
     */
    Optional<ResponseTemplateDO> findByIsDefault(Integer isDefault);

    /**
     * 将所有模板的 isDefault 重置为 0（设置新默认时先调用）
     */
    @Modifying
    @Query("UPDATE ResponseTemplateDO t SET t.isDefault = 0 WHERE t.isDefault = 1")
    int clearAllDefault();
}

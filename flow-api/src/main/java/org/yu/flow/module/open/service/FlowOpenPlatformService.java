package org.yu.flow.module.open.service;

import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.open.domain.FlowOpenPlatformDO;
import org.yu.flow.module.open.dto.FlowOpenCredentialDTO;
import org.yu.flow.module.open.dto.FlowOpenPlatformDTO;
import org.yu.flow.module.open.dto.OpenGrantItemDTO;
import org.yu.flow.module.open.query.FlowOpenPlatformQueryDTO;

import java.util.List;
import java.util.Map;

public interface FlowOpenPlatformService {

    FlowOpenPlatformDO save(FlowOpenPlatformDO body);

    FlowOpenPlatformDO update(FlowOpenPlatformDO body);

    void delete(String id);

    FlowOpenPlatformDTO getById(String id);

    PageBean<FlowOpenPlatformDTO> page(FlowOpenPlatformQueryDTO query);

    FlowOpenCredentialDTO createCredential(String platformId);

    FlowOpenCredentialDTO rotateCredential(String platformId, String credentialId);

    void disableCredential(String platformId, String credentialId);

    List<FlowOpenCredentialDTO> listCredentials(String platformId);

    List<String> listGrantedApiIds(String platformId);

    List<OpenGrantItemDTO> listGrantDetails(String platformId);

    void replaceGrants(String platformId, List<String> apiIds);

    void replaceGrants(String platformId, List<String> apiIds, Map<String, String> allowMethodsByApiId);

    /** 清理已下线/不存在接口的授权 */
    int purgeInvalidGrants(String platformId);
}

package org.yu.flow.module.oss.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.host.FlowHostDataScope;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssObjectDTO;
import org.yu.flow.module.oss.dto.OssPresignUrlDTO;
import org.yu.flow.module.oss.dto.OssUploadOptions;
import org.yu.flow.module.oss.dto.OssUploadResultDTO;
import org.yu.flow.module.oss.query.OssObjectQueryDTO;

import java.util.List;
import java.util.Map;

public interface OssObjectService {

    PageBean<OssObjectDTO> findPage(OssObjectQueryDTO queryDTO, FlowHostDataScope scope, FlowHostPrincipal principal);

    OssObjectDTO findById(String id, FlowHostDataScope scope, FlowHostPrincipal principal);

    void delete(String id, boolean force, FlowHostDataScope scope, FlowHostPrincipal principal);

    List<OssUploadResultDTO> upload(String profileCode, MultipartFile[] files, Map<String, String> bizFields,
                                    FlowHostPrincipal principal, HttpServletRequest request,
                                    OssUploadOptions options);

    void downloadOrPresign(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                           HttpServletRequest request, HttpServletResponse response);

    OssPresignUrlDTO presignUrl(String id, FlowHostPrincipal principal, FlowHostDataScope scope,
                                HttpServletRequest request);

    void packDownload(List<String> ids, FlowHostPrincipal principal, FlowHostDataScope scope,
                      HttpServletRequest request, HttpServletResponse response);
}

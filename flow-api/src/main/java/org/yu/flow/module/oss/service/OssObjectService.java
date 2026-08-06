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

    /**
     * 单条解析文件访问 URL (支持 Caffeine 堆内缓存)
     * @param fileId 文件 ID
     * @param profileCode 场景编码 (可选)
     * @param absolute 是否生成包含 HTTP/HTTPS 协议与 IP/Port/域名的全路径
     * @return 访问 URL (带或不带 IP/Port 前缀)
     */
    String resolveAccessUrl(String fileId, String profileCode, boolean absolute);

    /**
     * 单条解析文件详细元信息对象 (支持 Caffeine 堆内缓存)
     * @param fileId 文件 ID
     * @param profileCode 场景编码 (可选)
     * @param absolute URL 是否生成全路径
     * @return 包含文件名、大小、类型、URL 的 FlowOssFileResolvedDTO
     */
    org.yu.flow.module.oss.dto.FlowOssFileResolvedDTO resolveFileDetail(String fileId, String profileCode, boolean absolute);

    /**
     * 批量预热文件访问 URL 到 Caffeine 本地缓存 (单条 SQL IN 查询，消除 N+1)
     * @param fileIds 文件 ID 集合
     * @param absolute 是否生成全路径
     */
    void batchPreloadUrls(java.util.Collection<String> fileIds, boolean absolute);

    /**
     * 批量预热文件元信息对象到 Caffeine 本地缓存 (单条 SQL IN 查询，消除 N+1)
     * @param fileIds 文件 ID 集合
     * @param absolute 是否生成全路径
     */
    void batchPreloadDetails(java.util.Collection<String> fileIds, boolean absolute);
}

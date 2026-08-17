package org.yu.flow.module.oss.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.domain.OssDownloadLogDO;
import org.yu.flow.module.oss.dto.OssDownloadLogDTO;
import org.yu.flow.module.oss.query.OssDownloadLogQueryDTO;
import org.yu.flow.module.oss.repository.OssDownloadLogRepository;
import org.yu.flow.module.oss.service.OssDownloadLogService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.yu.flow.module.oss.config.ConditionalOnOssEnabled;

@ConditionalOnOssEnabled
@Service
public class OssDownloadLogServiceImpl implements OssDownloadLogService {

    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Resource
    private OssDownloadLogRepository ossDownloadLogRepository;

    @Override
    public PageBean<OssDownloadLogDTO> findPage(OssDownloadLogQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage(), queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
        );
        Specification<OssDownloadLogDO> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(queryDTO.getObjectId())) {
                predicates.add(cb.equal(root.get("objectId"), queryDTO.getObjectId()));
            }
            if (StrUtil.isNotBlank(queryDTO.getDownloadedBy())) {
                predicates.add(cb.equal(root.get("downloadedBy"), queryDTO.getDownloadedBy()));
            }
            if (StrUtil.isNotBlank(queryDTO.getResult())) {
                predicates.add(cb.equal(root.get("result"), queryDTO.getResult().trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<OssDownloadLogDO> page = ossDownloadLogRepository.findAll(spec, pageable);
        List<OssDownloadLogDTO> items = page.getContent().stream()
                .map(OssDownloadLogDTO::fromDO)
                .collect(Collectors.toList());
        return new PageBean<>(items, page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }

    @Override
    @Transactional
    public void writeLog(String objectId, FlowHostPrincipal principal, HttpServletRequest request,
                         String visibility, String result, String denyReason, long timeMs) {
        if (!"PRIVATE".equalsIgnoreCase(visibility) && !"DENIED".equals(result)
                && !"ERROR".equals(result) && !"NOT_FOUND".equals(result)) {
            // 公有文件不经此 API 成功下载；NOT_FOUND/DENIED/ERROR 仍记录
            if ("SUCCESS".equals(result)) {
                return;
            }
        }
        OssDownloadLogDO log = OssDownloadLogDO.builder()
                .objectId(objectId)
                .downloadedBy(principal != null ? principal.getUserId() : null)
                .downloadedByName(principal != null ? principal.getUsername() : null)
                .clientIp(resolveClientIp(request))
                .userAgent(request != null ? StrUtil.maxLength(request.getHeader("User-Agent"), 512) : null)
                .result(result)
                .denyReason(StrUtil.maxLength(denyReason, 512))
                .timeMs(timeMs)
                .createTime(LocalDateTime.now(ZONE_SH))
                .build();
        ossDownloadLogRepository.save(log);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                int comma = ip.indexOf(',');
                return StrUtil.maxLength(comma > 0 ? ip.substring(0, comma).trim() : ip.trim(), 64);
            }
        }
        return StrUtil.maxLength(request.getRemoteAddr(), 64);
    }
}

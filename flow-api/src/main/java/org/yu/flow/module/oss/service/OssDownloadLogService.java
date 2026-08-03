package org.yu.flow.module.oss.service;

import jakarta.servlet.http.HttpServletRequest;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.oss.dto.OssDownloadLogDTO;
import org.yu.flow.module.oss.query.OssDownloadLogQueryDTO;

public interface OssDownloadLogService {

    PageBean<OssDownloadLogDTO> findPage(OssDownloadLogQueryDTO queryDTO);

    void writeLog(String objectId, FlowHostPrincipal principal, HttpServletRequest request,
                  String visibility, String result, String denyReason, long timeMs);
}

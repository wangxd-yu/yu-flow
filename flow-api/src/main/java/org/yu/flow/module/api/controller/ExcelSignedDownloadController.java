package org.yu.flow.module.api.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.exception.ValidationException;
import org.yu.flow.module.api.service.ApiDataViewService;
import org.yu.flow.util.FlowObjectMapperUtil;
import org.yu.flow.util.ThrowableUtil;

/**
 * 短期签名 Excel 下载（浏览器直链，不要求业务 AppKey / 管理端 JWT）。
 * <p>鉴权由网关放行 + token 自身 HMAC/TTL 承担。</p>
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping("flow-api/download/excel")
public class ExcelSignedDownloadController {

    @Resource
    private ApiDataViewService apiDataViewService;

    @GetMapping("/{token:.+}")
    public void download(@PathVariable("token") String token, HttpServletResponse response) throws Exception {
        try {
            apiDataViewService.exportBySignedToken(token, response);
        } catch (ValidationException e) {
            String msg = e.getMessage() == null ? "下载失败" : e.getMessage();
            int status = (msg.contains("过期") || msg.contains("签名") || msg.contains("无效") || msg.contains("解析"))
                    ? HttpStatus.UNAUTHORIZED.value()
                    : HttpStatus.BAD_REQUEST.value();
            if (msg.contains("未启用") || msg.contains("不存在")) {
                status = HttpStatus.NOT_FOUND.value();
                msg = "接口不存在";
            }
            response.reset();
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(FlowObjectMapperUtil.flowObjectMapper()
                    .writeValueAsString(R.fail(status, msg)));
        } catch (Exception e) {
            log.error("[ExcelSignedDownload] 下载失败:\n{}", ThrowableUtil.getStackTrace(e));
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(FlowObjectMapperUtil.flowObjectMapper()
                        .writeValueAsString(R.fail(500, "Excel 下载失败")));
            }
        }
    }
}

package org.yu.flow.login;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.log.loginLog.domain.LoginLogDO;
import org.yu.flow.log.loginLog.service.LoginLogService;
import org.yu.flow.login.dto.LoginDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.core.IpInfo;

/**
 * 登录 Controller
 */
@Slf4j
@YuFlowApi
@RestController
@RequestMapping(value = {"flow-api"})
public class FlowLoginController {

    @Resource
    private LoginService loginService;

    @Resource
    private LoginLogService loginLogService;

    @Resource
    private Ip2regionSearcher ip2regionSearcher;

    @PostMapping("/login")
    public R<String> login(@RequestBody LoginDto loginDto, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        String token = loginService.login(loginDto);
        long duration = System.currentTimeMillis() - startTime;

        // 解析 IP 地理位置
        String region = "未知";
        try {
            if (ip2regionSearcher != null) {
                IpInfo ipInfo = ip2regionSearcher.memorySearch(ip);
                if (ipInfo != null) {
                    region = formatRegion(ipInfo.getAddress());
                }
            }
        } catch (Exception e) {
            log.error("IP region lookup failed for IP: {}", ip, e);
        }

        // 异步保存登录日志
        LoginLogDO logDO = LoginLogDO.builder()
                .account(loginDto.getUsername())
                .ip(ip)
                .region(region)
                .userAgent(userAgent)
                .duration(duration)
                .createTime(LocalDateTime.now())
                .build();

        if (token == null) {
            logDO.setStatus(0);
            logDO.setMsg("登录失败，用户名或密码错误");
            loginLogService.saveLog(logDO);
            return R.fail("登录失败，用户名或密码错误");
        }

        logDO.setStatus(1);
        logDO.setMsg("登录成功");
        loginLogService.saveLog(logDO);
        return R.ok(token);
    }

    /**
     * 格式化 IP 地区信息，去除无用“0”值和连续重复部分
     */
    private String formatRegion(String rawAddress) {
        if (rawAddress == null || rawAddress.isEmpty()) {
            return "未知";
        }
        String[] parts = rawAddress.split("\\|");
        StringBuilder sb = new StringBuilder();
        String lastPart = null;
        for (String part : parts) {
            if (part != null && !part.isEmpty() && !"0".equals(part) && !"null".equalsIgnoreCase(part)) {
                if (part.equals(lastPart)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("-");
                }
                sb.append(part);
                lastPart = part;
            }
        }
        return sb.length() == 0 ? "未知" : sb.toString();
    }

    /**
     * 获取客户端真实 IP
     * 依次检查常见的代理请求头，最后回退到 getRemoteAddr()
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR",
                "X-Real-IP"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一个真实客户端 IP
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

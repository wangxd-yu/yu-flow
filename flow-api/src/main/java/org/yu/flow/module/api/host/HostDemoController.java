package org.yu.flow.module.api.host;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宿主 MVC 演示接口，用于验收「同名拦截 / WRAP」提示与转发。
 * <p>默认开启；生产可设 {@code yu.flow.host-demo.enabled=false} 关闭。</p>
 */
@RestController
@RequestMapping("/yu-demo")
@ConditionalOnProperty(prefix = "yu.flow.host-demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HostDemoController {

    /**
     * GET /yu-demo/host-ping
     */
    @GetMapping("/host-ping")
    public Map<String, Object> hostPing(@RequestParam(required = false) String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("source", "host-mvc");
        body.put("path", "/yu-demo/host-ping");
        body.put("message", "宿主同名接口测试成功");
        body.put("name", name == null ? "world" : name);
        return body;
    }
}

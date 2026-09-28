package org.yu.flow.module.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yu.flow.annotation.YuFlowApi;
import org.yu.flow.dto.R;
import org.yu.flow.module.api.dto.PrivacyDevSessionDTO;
import org.yu.flow.module.api.privacy.PrivacyDevSessionService;
import org.yu.flow.module.rbac.support.RequirePerm;

/**
 * 仅本地 / 非生产：登录后签发 {@code X-Privacy-Key}，供 Apifox 等无法跑国密的客户端联调出站信封。
 * <p>生产 profile 与演示模式返回 404。不要把 {@code sm4KeyHex} 写入仓库或共享文档。</p>
 */
@YuFlowApi
@RestController
@RequestMapping("flow-api/dev")
@RequirePerm({"flow:api:view", "flow:api:write"})
public class PrivacyDevSessionController {

    private final PrivacyDevSessionService privacyDevSessionService;

    public PrivacyDevSessionController(PrivacyDevSessionService privacyDevSessionService) {
        this.privacyDevSessionService = privacyDevSessionService;
    }

    @GetMapping("/privacy-session")
    public R<PrivacyDevSessionDTO> privacySession() {
        if (!privacyDevSessionService.isAvailable()) {
            return R.fail(404, "当前环境未开放隐私调试会话");
        }
        return R.ok(privacyDevSessionService.createSession());
    }
}

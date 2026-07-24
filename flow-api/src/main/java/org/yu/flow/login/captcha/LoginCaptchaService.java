package org.yu.flow.login.captcha;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.cache.FlowRedisUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录图形验证码：Hutool 生成 + Redis 一次性校验。
 */
@Slf4j
@Service
public class LoginCaptchaService {

    private static final String KEY_PREFIX = "flow:login:captcha:";
    private static final long TTL_MINUTES = 5;
    /** 稍宽于前端容器，避免字符贴边被圆角裁切 */
    private static final int WIDTH = 160;
    private static final int HEIGHT = 56;
    private static final int CODE_LEN = 4;
    private static final int LINE_COUNT = 28;

    /**
     * 生成验证码。
     *
     * @return captchaId + imageBase64（含 data URI 前缀）
     */
    public Map<String, String> create() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(WIDTH, HEIGHT, CODE_LEN, LINE_COUNT);
        String code = captcha.getCode();
        String captchaId = IdUtil.fastSimpleUUID();
        String key = KEY_PREFIX + captchaId;
        try {
            boolean ok = FlowRedisUtil.set(key, code.toLowerCase(), TTL_MINUTES, TimeUnit.MINUTES);
            if (!ok) {
                throw new IllegalStateException("验证码缓存写入失败");
            }
        } catch (Exception e) {
            log.error("[LoginCaptcha] Redis 写入失败", e);
            throw new IllegalStateException("验证码服务暂不可用，请稍后重试");
        }

        Map<String, String> result = new HashMap<>(4);
        result.put("captchaId", captchaId);
        // getImageBase64Data 自带 data:image/png;base64, 前缀，前端可直接作 img src
        result.put("imageBase64", captcha.getImageBase64Data());
        return result;
    }

    /**
     * 校验并消费验证码（一次性）。
     *
     * @throws IllegalArgumentException 校验失败
     */
    public void verifyAndConsume(String captchaId, String captchaCode) {
        if (StrUtil.isBlank(captchaId) || StrUtil.isBlank(captchaCode)) {
            throw new IllegalArgumentException("请输入验证码");
        }
        String key = KEY_PREFIX + captchaId.trim();
        Object cached;
        try {
            cached = FlowRedisUtil.get(key);
        } catch (Exception e) {
            log.error("[LoginCaptcha] Redis 读取失败", e);
            throw new IllegalArgumentException("验证码服务暂不可用，请刷新后重试");
        }
        // 先删，防重放
        FlowRedisUtil.delete(key);

        if (cached == null) {
            throw new IllegalArgumentException("验证码已过期，请刷新");
        }
        String expect = String.valueOf(cached).trim().toLowerCase();
        String actual = captchaCode.trim().toLowerCase();
        if (!expect.equals(actual)) {
            throw new IllegalArgumentException("验证码错误");
        }
    }
}

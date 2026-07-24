package org.yu.flow.module.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.yu.flow.util.FlowObjectMapperUtil;

import java.util.concurrent.TimeUnit;

/**
 * 通用 Webhook POST（钉钉/企微自定义机器人可直接接收 JSON，或再包一层）。
 */
@Slf4j
@Component
public class AlertWebhookSender {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = FlowObjectMapperUtil.flowObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    public boolean postJson(String url, Object payload) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                if (code >= 200 && code < 300) {
                    return true;
                }
                log.warn("[AlertWebhook] 推送失败: status={}, body={}",
                        code, response.body() != null ? response.body().string() : "");
                return false;
            }
        } catch (Exception e) {
            log.warn("[AlertWebhook] 推送异常: {}", e.getMessage());
            return false;
        }
    }
}

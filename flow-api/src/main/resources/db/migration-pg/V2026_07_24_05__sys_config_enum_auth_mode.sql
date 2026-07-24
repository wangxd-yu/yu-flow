-- ENUM 配置约定（不改表结构）：value_type=ENUM，remark 以 [选项] 声明可选值
-- 格式：[VALUE|VALUE:展示名|...] 说明文字

UPDATE flow_sys_config
SET value_type = 'ENUM',
    remark = '[HOST:需管理端登录|OPEN:开放平台鉴权|NONE:无鉴权（仍受 allow-ingress-auth-none 约束）] 默认鉴权：NONE | HOST | OPEN（接口可覆盖）',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'INGRESS_DEFAULT_AUTH_MODE';

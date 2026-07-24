-- 安全加固：入站防护默认开启 + HOST 鉴权；开放入口默认要求宿主登录
UPDATE `flow_sys_config`
SET `config_value` = 'true',
    `remark`      = '已发布 API 入站防护总开关（安全默认开启；false=关闭后网关仍强制管理端 JWT）',
    `update_time` = NOW()
WHERE `config_key` = 'INGRESS_ENABLED';

UPDATE `flow_sys_config`
SET `config_value` = 'HOST',
    `remark`      = '默认鉴权模式：NONE|HOST|OPEN（安全默认 HOST，需管理端 JWT）',
    `update_time` = NOW()
WHERE `config_key` = 'INGRESS_DEFAULT_AUTH_MODE';

UPDATE `flow_sys_config`
SET `config_value` = 'true',
    `remark`      = '无 AppKey 访问已发布 API 时是否强制宿主登录（JWT）',
    `update_time` = NOW()
WHERE `config_key` = 'OPEN_REQUIRE_HOST_AUTH';

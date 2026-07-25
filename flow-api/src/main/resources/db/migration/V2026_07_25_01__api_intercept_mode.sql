-- 同名拦截模式：REPLACE（替换宿主）/ WRAP（包裹宿主增强）
ALTER TABLE `flow_api_info`
  ADD COLUMN `intercept_mode` varchar(16) NOT NULL DEFAULT 'REPLACE'
    COMMENT '同名拦截：REPLACE-替换执行引擎；WRAP-包裹转发宿主' AFTER `service_type`,
  ADD COLUMN `host_binding` text NULL
    COMMENT 'WRAP 宿主绑定 JSON：forward/targetPath/probePath' AFTER `intercept_mode`;

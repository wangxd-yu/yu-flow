-- 同名拦截模式：REPLACE（替换宿主）/ WRAP（包裹宿主增强）
ALTER TABLE flow_api_info
  ADD COLUMN IF NOT EXISTS intercept_mode varchar(16) NOT NULL DEFAULT 'REPLACE',
  ADD COLUMN IF NOT EXISTS host_binding text NULL;

COMMENT ON COLUMN flow_api_info.intercept_mode IS '同名拦截：REPLACE-替换执行引擎；WRAP-包裹转发宿主';
COMMENT ON COLUMN flow_api_info.host_binding IS 'WRAP 宿主绑定 JSON：forward/targetPath/probePath';
COMMENT ON COLUMN flow_api_info.service_type IS '服务驱动类型：DB、FLOW、JSON、STRING、HOST';

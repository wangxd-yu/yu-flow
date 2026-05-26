-- ═══════════════════════════════════════════════════════════════════════════
--  Seed Demo Directory & APIs
-- ═══════════════════════════════════════════════════════════════════════════

-- 1. 创建演示目录
INSERT INTO flow_page_directory (id, parent_id, name, sort, create_time, update_time)
VALUES ('demo_dir_001', NULL, '✨ 官方演示案例', 999, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = '✨ 官方演示案例';

-- 2. 插入静态 JSON 案例
INSERT INTO flow_api_info (id, name, url, directory_id, response_type, version, method, service_type, json_content, publish_status, level, info, deleted, create_time, update_time)
VALUES ('demo_api_json_1', '静态 JSON (Mock) 示例', '/demo/json/hello', 'demo_dir_001', 'OBJECT', '1.0.0', 'GET', 'JSON', 
'{
  "code": 200,
  "message": "Welcome to Yu-Flow OSS!",
  "data": {
    "version": "1.0.0-OSS",
    "features": ["Flow Engine", "API Governance", "Data Security", "Audit Log"]
  }
}', 1, 0, '返回一个固定的静态 JSON，适用于前端 Mock 联调阶段。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE publish_status = 1;

-- 3. 插入基础 DB 查询案例 (依赖系统内置的 flow_sys_config 表)
INSERT INTO flow_api_info (id, name, url, directory_id, response_type, version, method, service_type, datasource, sql_content, publish_status, level, info, deleted, create_time, update_time)
VALUES ('demo_api_db_1', '基础连库查询 (系统参数)', '/demo/db/sys-config', 'demo_dir_001', 'LIST', '1.0.0', 'GET', 'DB', 'master', 
'SELECT config_key, config_value, value_type, remark 
FROM flow_sys_config 
WHERE status = 1 
ORDER BY create_time DESC 
LIMIT 10', 1, 0, '基于 SQL 直接对外暴露 API。内置了防注入与参数化绑定能力。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE publish_status = 1;

-- 4. 插入流编排案例 (FLOW)
INSERT INTO flow_api_info (id, name, url, directory_id, response_type, version, method, service_type, dsl_content, publish_status, level, info, deleted, create_time, update_time)
VALUES ('demo_api_flow_1', '基础流编排数据清洗转换', '/demo/flow/transform', 'demo_dir_001', 'OBJECT', '1.0.0', 'POST', 'FLOW', 
'{
  "startStepId": "start_node",
  "steps": [
    {
      "id": "start_node",
      "type": "start",
      "name": "开始节点",
      "next": {
        "out": "set_node"
      }
    },
    {
      "id": "set_node",
      "type": "set",
      "name": "数据转换计算",
      "expression": "timestamp = T(java.lang.System).currentTimeMillis();\nresult = {code: 200, message: ''数据转换成功'', data: {timestamp: timestamp, requested: true}};",
      "next": {
        "out": "end_node"
      }
    },
    {
      "id": "end_node",
      "type": "end",
      "name": "结束输出",
      "expression": "result"
    }
  ]
}', 1, 0, '利用引擎内置的 Set 节点，对输入参数进行清洗、转化并组装返回结果。', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE publish_status = 1;

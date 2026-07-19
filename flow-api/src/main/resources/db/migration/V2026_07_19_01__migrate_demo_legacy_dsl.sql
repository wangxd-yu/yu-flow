-- 将演示 FLOW API 从遗留 start/set/end 迁移为 request/evaluate/response
UPDATE flow_api_info
SET dsl_content = '{
  "startStepId": "req",
  "steps": [
    {
      "id": "req",
      "type": "request",
      "name": "请求入口",
      "next": {
        "params": "eval_transform"
      }
    },
    {
      "id": "eval_transform",
      "type": "evaluate",
      "name": "数据转换",
      "language": "JavaScript",
      "expression": "({ code: 200, message: ''数据转换成功'', data: { requested: true, ts: Date.now() } })",
      "next": {
        "out": "resp"
      }
    },
    {
      "id": "resp",
      "type": "response",
      "name": "响应输出",
      "status": 200,
      "body": "${eval_transform}"
    }
  ]
}',
    info = '利用 Evaluate 节点对入参进行转换并组装返回结果。',
    update_time = NOW()
WHERE id = 'demo_api_flow_1';

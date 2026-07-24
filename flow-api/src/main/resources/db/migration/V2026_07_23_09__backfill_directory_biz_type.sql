-- 按资产引用回填目录 biz_type（仅被单一域引用才打标；多域/无引用保持空=共享）
UPDATE `flow_directory` d
SET `biz_type` = 'api'
WHERE (d.`biz_type` IS NULL OR d.`biz_type` = '')
  AND EXISTS (SELECT 1 FROM `flow_api_info` a WHERE a.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_task_info` t WHERE t.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_service_info` s WHERE s.`directory_id` = d.`id`);

UPDATE `flow_directory` d
SET `biz_type` = 'task'
WHERE (d.`biz_type` IS NULL OR d.`biz_type` = '')
  AND EXISTS (SELECT 1 FROM `flow_task_info` t WHERE t.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_api_info` a WHERE a.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_service_info` s WHERE s.`directory_id` = d.`id`);

UPDATE `flow_directory` d
SET `biz_type` = 'service'
WHERE (d.`biz_type` IS NULL OR d.`biz_type` = '')
  AND EXISTS (SELECT 1 FROM `flow_service_info` s WHERE s.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_api_info` a WHERE a.`directory_id` = d.`id`)
  AND NOT EXISTS (SELECT 1 FROM `flow_task_info` t WHERE t.`directory_id` = d.`id`);

-- 为分页响应模板补充 page 字段，兼容后端通用分页命名。
-- current 保留给 Ant Design ProTable / amis 使用。

UPDATE flow_response_template
SET page_wrapper = REPLACE(
        page_wrapper,
        '"items": "$.items", "total": "$.total"',
        '"items": "$.items", "page": "$.page", "total": "$.total"'
    )
WHERE page_wrapper IS NOT NULL
  AND page_wrapper LIKE '%"current": "$.current"%'
  AND page_wrapper NOT LIKE '%"page": "$.page"%';

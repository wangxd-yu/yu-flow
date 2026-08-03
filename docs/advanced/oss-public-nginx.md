# 对象存储公有文件 Nginx 直出样例

Yu Flow 公有上传会返回 `publicPath`（及可选 `publicUrl`）。浏览器应直链访问，**不要**走 `/flow-api/oss/objects/{id}/content`。

## 反代 MinIO 公有桶（推荐）

连接配置：

- `public_bucket` = `public-bucket`
- `public_base_url` = `https://cdn.example.com/files`
- `public_access_mode` = `NGINX_PROXY`（Nginx 持凭证）或 `ANON`（桶匿名可读）

```nginx
location /files/ {
    proxy_pass http://minio:9000/public-bucket/;
    proxy_set_header Host $host;
    proxy_http_version 1.1;
    # 若桶非匿名，可在此注入 MinIO 只读凭证（勿暴露管理端口）
}
```

业务侧对象键若带前缀 `tenantA/avatar/...`，则公网 URL 形如：

`https://cdn.example.com/files/tenantA/avatar/...`

请保证 `public_base_url` + `public_path` 与 Nginx `location` / `proxy_pass` 一致。

## 安全注意

- 不要把 MinIO Console / 管理端口直接暴露公网。
- 隐私文件必须走 `GET /flow-api/oss/objects/{id}/content`（鉴权 + 审计）。
- 嵌入宿主时实现 `FlowHostPrincipalProvider` / `FlowHostDataScopeProvider` 以启用部门/用户列表数据范围；未实现时使用内置 JWT（SELF / admin→ALL），管理页会提示对接。

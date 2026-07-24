# 接口数据查看与 Excel 导出

面向 **DB 模式**接口（`PAGE` / `LIST` / `OBJECT`），在管理端提供数据预览与 Excel 导出；并支持可选的**对外** `/export` 与短期下载链。  
**未开启对外导出时，已发布业务 URL 仍只返回 JSON**，契约与开放平台调用不受影响。

## 普通接口 vs 导出地址

| | 普通接口（已发布 path） | 管理端导出 | 对外 Excel（可选） | 短期下载链（可选） |
|--|------------------------|------------|-------------------|-------------------|
| 地址示例 | `{SYSTEM_PREFIX}/你的业务path` | `POST /flow-api/api/{id}/data/export` | `{path}/export`、`/flow-api/open{path}/export` | `GET /flow-api/download/excel/{token}` |
| 返回 | JSON（契约稳定） | `.xlsx` 文件流 | `.xlsx` | `.xlsx` |
| 鉴权 | 入站 / 开放 / 管理端 JWT | 管理端权限 | 与 JSON **同权** | 链上 HMAC+TTL |
| 调用方 | App / 开放平台 / 前端业务 | 运营/研发 | 业务系统 | 浏览器直链 |

**不要**在同一业务 path 上用 `?format=xlsx` 混 JSON 与 Excel。对外下载必须走独立后缀 `/export` 或短期链。

## 入口

- 接口列表 → 操作列「数据查看」
- 接口编辑页 Header →「数据查看」

仅当接口为 **DB 模式**且响应类型为 **PAGE / LIST / OBJECT** 时可点击；  
新增 / 修改 / 删除等写操作（如响应类型 `UPDATE`）以及 FLOW / JSON / STRING 接口会显示为禁用（与「查看缓存」一致），悬停可看原因。

抽屉内可：

1. 按请求契约填写查询参数并预览  
2. 导出 Excel（动态表头或公司模板填充；按钮文案会提示当前模式）  
3. 「列配置」：中文表头 / 显隐 / 是否导出 / 行数上限  
4. 「导出模板」：拖拽上传公司标准 `.xlsx`、列 → 占位符映射  
5. 「对外下载」：启用开关、URL 预览、复制、签发短期链  

未保存的配置修改会在抽屉顶部提示，避免漏保存。

## 对外 Excel 下载（业务方）

### 开关

在「数据查看 → 对外下载」中打开 **启用对外 Excel 下载**（`viewExportConfig.openExportEnabled`，默认 `false`），保存草稿并**发布**后生效。

相关字段：

| 字段 | 默认 | 说明 |
|------|------|------|
| `openExportEnabled` | `false` | 是否开放 `{url}/export` |
| `signedLinkEnabled` | `true` | 是否允许管理端签发短期链 |
| `signedLinkTtlSeconds` | `300` | 短期链 TTL（上限 3600） |

未开启时命中 `/export` → **统一 404**（直连与 open 入口一致）。API URL 本身不得以 `/export` 结尾。

### 调用方式

1. **开放 / 入站同权**  
   - Method 与业务接口相同  
   - 地址：`{业务 path}/export` 或 `/flow-api/open{业务 path}/export`  
   - 验签 path **包含** `/export`；查询参数 / Body 与业务契约一致  
   - 仅使用**已发布**快照；行数受 `maxExportRows` 约束；支持 DYNAMIC / TEMPLATE  

2. **短期签名下载链**  
   - 管理端：`POST /flow-api/api/{id}/data/export-link`（`flow:api:write`）  
   - 返回 `{ url, expireAt, ttlSeconds }`，浏览器 `GET` 即可下载  
   - 链本身即授权，不再要求 AppKey；过期 / 签名失败 → 401  
   - Demo 锁定资产禁止签发  

设计说明见 [Plan/2026-07-24-业务Excel对外下载.md](../../../Plan/2026-07-24-业务Excel对外下载.md)。

## 列中文名从哪里来

优先级：

1. 「列配置」中显式填写的表头  
2. 响应契约字段的 **中文名**（`title`）  
3. 字段说明（`description`）  
4. 字段名本身  

建议在「API 文档定义 · 响应」中为字段填写中文名，再在数据查看里点「从响应契约同步列」（会保留已有 `templateKey` / 显隐设置）。

## 草稿与已发布

- 默认「使用草稿」：按当前草稿 SQL / 契约 / 列配置查询  
- 关闭后：按**已发布快照**执行（未发布不可关）  
- `viewExportConfig`（含 `exportMode`、`openExportEnabled`、列映射）保存在草稿，**需发布**后才会进入线上快照  
- 模板文件存在表 `flow_api_excel_template`，按接口一份，上传即覆盖（不进 git）

## 导出行为

| 响应类型 | 预览 | 导出 |
|----------|------|------|
| PAGE | 分页表格 | 全量（受最大行数限制，默认 5 万） |
| LIST | 表格 | 全量（同上限） |
| OBJECT | 只读表单 | 单行宽表 |

`exportMode`：

| 模式 | 说明 |
|------|------|
| `DYNAMIC` | 按列配置动态写表头（默认） |
| `TEMPLATE` | 用已上传公司模板 EasyExcel **填充** |

自动回退（不白屏）：

| 响应头 `X-Export-Fallback` | 含义 |
|----------------------------|------|
| `TEMPLATE_MISSING` | 配置了模板模式但文件不存在 |
| `TEMPLATE_NO_LIST_PLACEHOLDER` | 模板内未检测到 `{.字段}` |
| `TEMPLATE_FILL_FAILED` | EasyExcel 填充异常 |

同时返回：

- `X-Export-Mode`：`TEMPLATE` / `DYNAMIC`  
- `X-Export-Fallback-Message`：UTF-8 URL 编码的中文说明（前端 toast 用）  
- `X-Export-Rows`：导出行数  

填充失败时会**复用已查询数据**再写动态表头，避免二次查库。

初始化 SQL：

```text
flow-api/sql-mysql/flow_api_info.sql（含 view_export_config）
flow-api/sql-mysql/flow_api_excel_template.sql
flow-api/sql-pg/ 同名结构（PG）
```

## 公司 Excel 模板灌数（无需安装 Office）

后端使用 EasyExcel 4 填充，**不需要**本机 Excel/Office 插件，也**不提供**前端在线改表（公司版式请用桌面 Excel 做好再上传）。

### 模板约定（强制）

1. 列表数据区占位符：`{.fieldName}`（点号表示 list）  
2. 单值区：`{exportTime}`、`{apiName}`  
3. **一个 Sheet 一个列表区域**（P0）  
4. 先下载「示例模板」（含「数据」+「使用说明」两个 Sheet），再按公司样式改表头/合并单元格后上传  

示例：

| A | B | C |
|---|---|---|
| `{apiName}` 导出 `{exportTime}` | | |
| 订单号 | 客户名 | 金额 |
| `{.orderNo}` | `{.customerName}` | `{.amount}` |

### 上传校验（产品化）

- 仅 `.xlsx`；禁止 `.xlsm` / `.xls` / `.xlsb`  
- ZIP 魔数 + 必须含 `workbook.xml`  
- 拒绝包内 `vbaProject.bin` 等宏痕迹  
- ≤ 2MB  
- 上传后扫描是否含 `{.xxx}`：缺少时**仍可保存**，但会 warning，导出时回退动态表头  

### 管理端步骤

1. 打开「数据查看」→「导出模板」  
2. 下载示例模板，按公司表头改好占位符  
3. 拖拽或点击上传 `.xlsx`  
4. 确认映射表「数据字段 → 模板 key」（建议字母数字下划线；可一键重置为字段名）  
5. 导出模式选「模板填充」，保存配置并**发布**  
6. 点「导出 Excel」：有合法模板则填充；否则 toast 说明回退原因  

### 配置字段（`viewExportConfig`）

```json
{
  "enabled": true,
  "openExportEnabled": false,
  "signedLinkEnabled": true,
  "signedLinkTtlSeconds": 300,
  "sheetName": "数据",
  "maxExportRows": 50000,
  "exportMode": "TEMPLATE",
  "templateFileId": "tpl_xxx",
  "templateSheetNo": 0,
  "columns": [
    { "field": "orderNo", "header": "订单号", "templateKey": "orderNo" }
  ]
}
```

### 管理端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/flow-api/api/{id}/data/export-template` | 模板元信息（含 `hasListPlaceholder` / `warning`） |
| POST | `/flow-api/api/{id}/data/export-template` | 上传（multipart `file`） |
| DELETE | `/flow-api/api/{id}/data/export-template` | 删除并回退 DYNAMIC |
| GET | `/flow-api/api/{id}/data/export-template/sample` | 示例模板 |
| GET | `/flow-api/api/{id}/data/export-template/file` | 下载已上传文件 |
| POST | `/flow-api/api/{id}/data/export-link` | 签发短期下载链 |

## 权限

- 预览 / 模板元信息 / 示例下载：`flow:api:view` 或 `flow:api:write`  
- 导出 / 上传 / 删除模板 / 签发下载链：`flow:api:write`  
- 短期链下载：无需管理端 JWT / AppKey（token 自带授权）  
- Demo 锁定资产禁止改模板、导出与签发

## 排错

| 现象 | 排查 |
|------|------|
| 导出空表 | 列配置字段与 SQL 结果列不一致；动态模式会尽量对齐 SQL 列 |
| 模板有表头但无数据行 | 检查是否写成 `{field}` 而不是 `{.field}`；key 是否与映射表一致 |
| toast 提示回退动态 | 看 `X-Export-Fallback`；重新下载示例对照占位符 |
| 上传失败 | 扩展名、2MB、是否含宏、是否伪 xlsx |
| `/export` 404 | 是否开启 `openExportEnabled` 并已发布；URL 是否多写 `/export` |
| 短期链 401 | TTL 过期或签名密钥变更 |
| 业务 JSON 变了？ | 不应；JSON path 与 `/export` 分离 |

## 非目标（后续）

- OpenAPI 自动附带 export path；吊销 token（P1）  
- 异步大导出（P2）  
- 模板内更多单值变量校验提示  
- FLOW / JSON / STRING 模式（可按 `itemsPath` 扩展）  
- 前端在线编辑 Excel 模板  
- 在对外 path 上用 `?format=xlsx` 改返回类型（不推荐）

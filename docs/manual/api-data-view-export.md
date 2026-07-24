# 接口数据查看与 Excel 导出

面向 **DB 模式**接口（`PAGE` / `LIST` / `OBJECT`），在管理端提供数据预览与 Excel 导出。  
**对外已发布 URL 仍只返回 JSON**，契约与开放平台调用不受影响。

## 普通接口 vs 导出地址

| | 普通接口（已发布 path） | 导出（管理端） |
|--|------------------------|----------------|
| 地址示例 | `/flow-api/你的业务path` | `POST /flow-api/api/{id}/data/export` |
| 返回 | JSON（契约稳定） | `.xlsx` 文件流 |
| 调用方 | App / 开放平台 / 前端业务 | 运营/研发在控制台 |

**不要**在同一业务 path 上混 JSON 与 Excel（会破坏 OpenAPI、网关包装与签名）。若二期要对外部提供下载，应使用独立 path 或短期下载链。

## 入口

- 接口列表 → 操作列「数据查看」
- 接口编辑页 Header →「数据查看」

抽屉内可：

1. 按请求契约填写查询参数并预览  
2. 导出 Excel（动态表头或公司模板填充；按钮文案会提示当前模式）  
3. 「列配置」：中文表头 / 显隐 / 是否导出 / 行数上限  
4. 「导出模板」：拖拽上传公司标准 `.xlsx`、列 → 占位符映射  

未保存的配置修改会在抽屉顶部提示，避免漏保存。

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
- `viewExportConfig`（含 `exportMode`、列映射）保存在草稿，**需发布**后才会进入线上快照  
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
flow-api/sql/alter_api_view_export_config.sql
flow-api/sql/alter_api_excel_template.sql
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

## 权限

- 预览 / 模板元信息 / 示例下载：`flow:api:view` 或 `flow:api:write`  
- 导出 / 上传 / 删除模板：`flow:api:write`  
- Demo 锁定资产禁止改模板与导出（DemoMode）

## 排错

| 现象 | 排查 |
|------|------|
| 导出空表 | 列配置字段与 SQL 结果列不一致；动态模式会尽量对齐 SQL 列 |
| 模板有表头但无数据行 | 检查是否写成 `{field}` 而不是 `{.field}`；key 是否与映射表一致 |
| toast 提示回退动态 | 看 `X-Export-Fallback`；重新下载示例对照占位符 |
| 上传失败 | 扩展名、2MB、是否含宏、是否伪 xlsx |
| 业务 JSON 变了？ | 不应；导出是独立管理端 path |

## 非目标（后续）

- 模板内更多单值变量校验提示（P1）  
- 开放平台独立 export path；多 Sheet 多列表区（P2）  
- FLOW / JSON / STRING 模式（可按 `itemsPath` 扩展）  
- 前端在线编辑 Excel 模板  
- 在对外 path 上用 `?format=xlsx` 改返回类型（不推荐）

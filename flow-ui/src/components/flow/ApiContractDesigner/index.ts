export { default as SchemaTreeTable } from './SchemaTreeTable';
export { default as useSchemaDrawer } from './useSchemaDrawer';
export { treeToSchema, schemaToTree } from './schemaUtils';
export type {
  ApiContract,
  BaseInfo,
  RequestConfig,
  ResponseConfig,
  SchemaNode,
  SchemaType,
  HttpMethod,
  BodyType,
} from './types';

/**
 * 契约 UI 说明：
 * - 线上路径：接口表单的 ReqSchemaPanel / ResSchemaPanel（及服务契约）直接使用
 *   SchemaTreeTable / types / useSchemaDrawer。
 * - 旧的整页 `ApiContractDesigner` 壳组件已删除（从未挂载）。
 * - 多响应码：OpenAPI 后端已支持 `responses` map；前端当前仅编辑单一 statusCode，
 *   其余键在保存时若已存在于草稿 JSON 中会被保留，完整 UI 后续再做。
 */

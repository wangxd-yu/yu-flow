/**
 * API JSON 生成工厂（插件化版本）
 *
 * 通过遍历已注册的 FeatureTrait 插件的 generateBackendApi() 方法，
 * 动态拼装 API 配置对象数组，彻底消除硬编码的 CRUD 分支。
 *
 * 宏替换规则（生成的 SQL 模板中使用）：
 *   普通变量：  ${fieldName}
 *   模糊搜索：  like '%${fieldName}%'
 *   雪花主键：  ${#ID.SNOWFLAKE}
 *   当前时间戳：${#TIME.DATE-TIME-FULL}
 */

import type { DataModel } from './amisGenerator';
import { TRAIT_LIST } from './traits';
import type { CapabilityRow } from './traits';

// ---- 主函数 ----

/**
 * 根据启用的 Trait 生成 API 配置对象数组
 *
 * @param model          数据模型（需包含 fieldsSchema）
 * @param directoryId    全局目录 ID
 * @param capabilities   页面包含的功能能力集合
 * @param datasourceCode 数据源编码，默认 [DEFAULT]
 * @param customBaseUrl  自定义 API 根路径（可选，默认 /dynamic/{tableName}）
 * @returns API 配置对象数组，可直接提交给后端批量创建接口
 */
export const generateCrudApis = (
  model: DataModel,
  directoryId: string,
  capabilities: CapabilityRow[],
  datasourceCode: string = '[DEFAULT]',
  customBaseUrl?: string,
): any[] => {
  const tableName = model.tableName;

  // API URL 基础路径：优先使用自定义路径，否则使用默认规则（同时截断查询参数）
  let baseUrl = customBaseUrl || '/dynamic/' + tableName;
  if (baseUrl.includes('?')) {
    baseUrl = baseUrl.split('?')[0];
  }

  // 公共属性
  const commonProps = {
    directoryId,
    serviceType: 'DB',
    datasource: datasourceCode,
    publishStatus: 1,
  };

  // 遍历 capabilities，仅处理 cap.dataStrategy === 'generate' 且拥有 sourceTrait 的能力
  const apis: any[] = [];
  for (const cap of capabilities) {
    if (cap.dataStrategy !== 'generate' || !cap.sourceTrait) continue;

    const trait = TRAIT_LIST.find((t) => t.code === cap.sourceTrait);
    if (!trait || !trait.generateBackendApi) continue;

    const api = trait.generateBackendApi(model, commonProps, baseUrl, cap);
    if (api) {
      api._traitCode = trait.code;
      // 核心覆盖：强制使用前端传递的路径和方法覆盖默认值
      api.url = cap.apiPath;
      api.method = cap.apiMethod;
      api.name = cap.name;
      apis.push(api);
    }
  }

  return apis;
};

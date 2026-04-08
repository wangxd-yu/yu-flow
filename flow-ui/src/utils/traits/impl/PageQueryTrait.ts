import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import type { DataModel, FieldSchema } from '../../amisGenerator';

// ---- 内部工具函数（从 apiGenerator 迁入） ----

function parseFields(model: DataModel): FieldSchema[] {
  const raw = model.fieldsSchema;
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    return JSON.parse(raw) as FieldSchema[];
  } catch {
    console.error('[PageQueryTrait] 解析 fieldsSchema 失败', raw);
    return [];
  }
}

function toColumnName(fieldId: string): string {
  if (fieldId.includes('_')) return fieldId;
  return fieldId.replace(/([A-Z])/g, '_$1').toLowerCase();
}

export const PageQueryTrait: FeatureTrait = {
  code: 'page',
  name: '分页查询',
  group: 'query',
  isDefault: true,

  // ── UI 约束元数据 ──
  allowedPositions: ['toolbar'],
  allowedInteractions: ['local'],
  hideDataStrategy: true,

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'page',
      name: '分页查询',
      method: 'GET',
      url: `/dynamic/${moduleCode}/page`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    const fields = parseFields(model);
    const bizFields = fields.filter(
      (f) => (f.fieldId || '').toLowerCase() !== 'id',
    );

    // 构建带模糊搜索条件的分页查询 SQL
    let pageSql = 'select * from ' + model.tableName + ' where 1=1';
    bizFields.forEach((f) => {
      if (f.isSearchable) {
        const col = toColumnName(f.fieldId);
        pageSql += " and " + col + " like '%${" + f.fieldId + "}%'";
      }
    });

    return {
      ...commonProps,
      name: model.name + '分页查询',
      url: baseUrl + '/page',
      method: 'GET',
      responseType: 'PAGE',
      config: pageSql,
    };
  },

  // ---- 3. 前端查询：托管给 CRUD 框架 ----
  getAmisNode: (_cap: CapabilityRow, _context: any): any => {
    return null;
  },
};

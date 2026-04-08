import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import { getAmisApiPrefix } from '../../env';
import type { DataModel, FieldSchema } from '../../amisGenerator';

// ---- 内部工具函数（从 apiGenerator 迁入） ----

function parseFields(model: DataModel): FieldSchema[] {
  const raw = model.fieldsSchema;
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    return JSON.parse(raw) as FieldSchema[];
  } catch {
    console.error('[InsertTrait] 解析 fieldsSchema 失败', raw);
    return [];
  }
}

function toColumnName(fieldId: string): string {
  if (fieldId.includes('_')) return fieldId;
  return fieldId.replace(/([A-Z])/g, '_$1').toLowerCase();
}

export const InsertTrait: FeatureTrait = {
  code: 'insert',
  name: '新增',
  group: 'toolbar',
  isDefault: true,

  // ── UI 约束元数据 ──
  allowedPositions: ['toolbar'],
  allowedInteractions: ['drawer', 'dialog', 'page'],

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'insert',
      name: '新增',
      method: 'POST',
      url: `/dynamic/${moduleCode}`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    const fields = parseFields(model);
    const bizFields = fields.filter(
      (f) => (f.fieldId || '').toLowerCase() !== 'id',
    );

    // 列名列表：id, col1, col2, ...
    const insertColumns = ['id']
      .concat(bizFields.map((f) => toColumnName(f.fieldId)))
      .join(', ');

    // 处理主键 ID 的宏变量
    const idField = fields.find((f) => (f.fieldId || '').toLowerCase() === 'id');
    const idMacro = idField?.macroValue ? idField.macroValue : '${#ID.SNOWFLAKE}';

    // 值占位列表：${#ID.SNOWFLAKE}, ${field1}, ${field2}, ...
    const insertValues = [idMacro]
      .concat(bizFields.map((f) => f.macroValue ? f.macroValue : '${' + f.fieldId + '}'))
      .join(', ');

    return {
      ...commonProps,
      name: model.name + '新增',
      url: baseUrl,
      method: 'POST',
      responseType: 'UPDATE',
      config:
        'INSERT INTO ' +
        model.tableName +
        ' (' +
        insertColumns +
        ') VALUES (' +
        insertValues +
        ')',
    };
  },

  // ---- 3. 动态 UI：新增按钮 ----
  getAmisNode: (cap: CapabilityRow, context: any): any => {
    const actionType = cap.interaction === 'drawer' ? 'drawer' : 'dialog';
    const formElement = {
      title: cap.name,
      body: {
        type: 'form',
        api: `${cap.apiMethod.toLowerCase()}:${getAmisApiPrefix()}${cap.apiPath}`,
        body: context.formBody || [],
      },
    };

    return {
      type: 'button',
      actionType,
      label: cap.name,
      icon: 'fa fa-plus pull-left',
      primary: true,
      [actionType]: formElement,
    };
  },
};

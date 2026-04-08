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
    console.error('[UpdateTrait] 解析 fieldsSchema 失败', raw);
    return [];
  }
}

function toColumnName(fieldId: string): string {
  if (fieldId.includes('_')) return fieldId;
  return fieldId.replace(/([A-Z])/g, '_$1').toLowerCase();
}

export const UpdateTrait: FeatureTrait = {
  code: 'update',
  name: '修改',
  group: 'row-action',
  isDefault: true,

  // ── UI 约束元数据 ──
  allowedPositions: ['row'],
  allowedInteractions: ['dialog', 'drawer', 'page'],

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'update',
      name: '修改',
      method: 'PUT',
      url: `/dynamic/${moduleCode}?id=\${id}`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    const fields = parseFields(model);
    const bizFields = fields.filter(
      (f) => (f.fieldId || '').toLowerCase() !== 'id',
    );

    // SET 语句：col1 = ${field1}, col2 = ${field2}, ...
    const setClauses: string[] = [];
    bizFields.forEach((f) => {
      const col = toColumnName(f.fieldId);
      if (f.macroValue) {
        setClauses.push(col + ' = ' + f.macroValue);
      } else {
        setClauses.push(col + ' = ${' + f.fieldId + '}');
      }
    });

    // 如果表中有 update_time 字段，自动追加时间戳
    const hasUpdateTime = fields.some(
      (f) =>
        (f.fieldId || '').toLowerCase() === 'updatetime' ||
        (f.fieldId || '').toLowerCase() === 'update_time',
    );
    if (hasUpdateTime) {
      const filtered = setClauses.filter(
        (s) => !s.startsWith('update_time'),
      );
      filtered.push('update_time = ${#TIME.DATE-TIME-FULL}');
      setClauses.length = 0;
      setClauses.push(...filtered);
    }

    return {
      ...commonProps,
      name: model.name + '修改',
      url: baseUrl,
      method: 'PUT',
      responseType: 'UPDATE',
      config:
        'UPDATE ' +
        model.tableName +
        ' SET ' +
        setClauses.join(', ') +
        ' WHERE id = ${id}',
    };
  },

  // ---- 3. 动态 UI：编辑按钮 ----
  getAmisNode: (cap: CapabilityRow, context: any): any => {
    const actionType = cap.interaction === 'dialog' ? 'dialog' : 'drawer';
    const formElement = {
      title: cap.name,
      ...(actionType === 'drawer' ? { position: 'right', size: 'lg' } : {}),
      body: {
        type: 'form',
        api: `${cap.apiMethod.toLowerCase()}:${getAmisApiPrefix()}${cap.apiPath}`,
        body: context.formBody || [],
      },
    };

    return {
      type: 'button',
      level: 'link',
      actionType,
      label: cap.name,
      [actionType]: formElement,
    };
  },
};

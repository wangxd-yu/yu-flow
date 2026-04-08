import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import { getAmisApiPrefix } from '../../env';
import type { DataModel, FieldSchema } from '../../amisGenerator';

function parseFields(model: DataModel): FieldSchema[] {
  const raw = model.fieldsSchema;
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    return JSON.parse(raw) as FieldSchema[];
  } catch {
    console.error('[QuickEditTrait] 解析 fieldsSchema 失败', raw);
    return [];
  }
}

function toColumnName(fieldId: string): string {
  if (fieldId.includes('_')) return fieldId;
  return fieldId.replace(/([A-Z])/g, '_$1').toLowerCase();
}

export const QuickEditTrait: FeatureTrait = {
  code: 'quick-edit',
  name: '行内快捷编辑',
  group: 'column-action', // 归属到全新的列级操作分组
  isDefault: false, // 默认不勾选

  // ── UI 约束元数据 ──
  allowedPositions: ['inline'], // 防呆：只能是单元格内
  allowedInteractions: ['ajax'], // 交互：直接发请求保存
  isComposite: true, // 屏蔽外部表单

  // ── 动态表单结构 ──
  configSchema: [
    {
      name: 'editFields',
      label: '允许编辑的列',
      type: 'field-select-multiple', // 渲染为字段下拉多选框
      required: true,
      defaultValue: [],
      tooltip: '勾选后，这些列在表格中可以直接点击进行修改',
    },
    {
      name: 'saveImmediately',
      label: '修改后立即保存',
      type: 'switch',
      defaultValue: true,
      tooltip: '关闭后，需点击表格底部的统一保存按钮',
    }
  ],

  // ---- API 生成逻辑 ----
  injectApis: (moduleCode) => [
    { action: 'quick-edit', name: '快捷保存', method: 'PUT', url: `/dynamic/${moduleCode}/quick-edit` },
  ],

  // ---- 后端 API 配置生成（基于前端配置项动态限定范围） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string, cap?: CapabilityRow): any => {
    const fields = parseFields(model);
    const editFields: string[] = cap?.traitConfig?.editFields || [];

    // 仅筛选处于白名单中、且不是主键的字段
    const bizFields = fields.filter(
      (f) =>
        (f.fieldId || '').toLowerCase() !== 'id' &&
        editFields.includes((f.fieldId || (f as any).name || (f as any).enName) as string)
    );

    if (bizFields.length === 0) {
      return null;
    }

    const setClauses: string[] = [];
    bizFields.forEach((f) => {
      const col = toColumnName(f.fieldId);
      if (f.macroValue) {
        setClauses.push(col + ' = ' + f.macroValue);
      } else {
        setClauses.push(col + ' = ${' + f.fieldId + '}');
      }
    });

    const hasUpdateTime = fields.some(
      (f) =>
        (f.fieldId || '').toLowerCase() === 'updatetime' ||
        (f.fieldId || '').toLowerCase() === 'update_time',
    );
    if (hasUpdateTime && setClauses.length > 0) {
      const filtered = setClauses.filter(
        (s) => !s.startsWith('update_time'),
      );
      filtered.push('update_time = ${#TIME.DATE-TIME-FULL}');
      setClauses.length = 0;
      setClauses.push(...filtered);
    }

    return {
      ...commonProps,
      name: model.name + '快捷保存',
      url: baseUrl + '/quick-edit',
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

  // ---- AST 突变逻辑 ----
  mutateAmisSchema: (schema: any, cap: CapabilityRow, _context: any) => {
    const editFields = cap.traitConfig?.editFields || [];
    const saveImmediately = cap.traitConfig?.saveImmediately !== false;

    // 获取表格列引用
    const columns = schema?.body?.columns;
    if (Array.isArray(columns) && editFields.length > 0) {
      columns.forEach((col: any) => {
        if (editFields.includes(col.name)) {
          col.quickEdit = {
            mode: 'inline',
            saveImmediately: saveImmediately,
          };
        }
      });
    }

    // 根据保存模式向 CRUD 根节点注入 API
    if (schema?.body && cap.apiMethod && cap.apiPath) {
      const apiStr = `${cap.apiMethod.toLowerCase()}:${getAmisApiPrefix()}${cap.apiPath}`;
      if (saveImmediately) {
        schema.body.quickSaveItemApi = apiStr;
      } else {
        schema.body.quickSaveApi = apiStr;
      }
    }
  },
};

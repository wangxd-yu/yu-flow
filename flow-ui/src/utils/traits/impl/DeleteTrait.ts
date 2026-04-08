import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import { getAmisApiPrefix } from '../../env';
import type { DataModel } from '../../amisGenerator';

export const DeleteTrait: FeatureTrait = {
  code: 'delete',
  name: '删除',
  group: 'row-action',
  isDefault: true,

  // ── UI 约束元数据 ──
  allowedPositions: ['row'],
  allowedInteractions: ['ajax'],

  // ── 配置表单结构 ──
  configSchema: [
    {
      name: 'confirmText',
      label: '二次确认提示语',
      type: 'input',
      defaultValue: '确定要删除该条数据吗？',
      required: true,
      tooltip: '操作前弹出的确认提示文案',
    },
  ],

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'delete',
      name: '删除',
      method: 'DELETE',
      url: `/dynamic/${moduleCode}?id=\${id}`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    return {
      ...commonProps,
      name: model.name + '删除',
      url: baseUrl,
      method: 'DELETE',
      responseType: 'UPDATE',
      // 使用预编译 #{id} 防注入
      config: 'DELETE FROM ' + model.tableName + ' WHERE id IN (#{id})',
    };
  },

  // ---- 3. 动态 UI 生成 ----
  getAmisNode: (cap: CapabilityRow, _context: any): any => {
    const confirmText = cap.traitConfig?.confirmText || '确定要删除该条数据吗？';
    return {
      type: 'button',
      level: 'link',
      className: 'text-danger',
      actionType: 'ajax',
      label: cap.name,
      confirmText,
      api: `${cap.apiMethod.toLowerCase()}:${getAmisApiPrefix()}${cap.apiPath}`,
    };
  },
};

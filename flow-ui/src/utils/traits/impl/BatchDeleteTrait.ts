import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import { getAmisApiPrefix } from '../../env';
import type { DataModel } from '../../amisGenerator';

export const BatchDeleteTrait: FeatureTrait = {
  code: 'batch-delete',
  name: '批量删除',
  group: 'toolbar',
  isDefault: true,

  // ── UI 约束元数据 ──
  allowedPositions: ['batch'],
  allowedInteractions: ['ajax'],

  // ── 配置表单结构 ──
  configSchema: [
    {
      name: 'confirmText',
      label: '二次确认提示语',
      type: 'input',
      defaultValue: '确定要删除选中的数据吗？',
      required: true,
      tooltip: '操作前弹出的确认提示文案',
    },
  ],

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'batch-delete',
      name: '批量删除',
      method: 'DELETE',
      url: `/dynamic/${moduleCode}/batch?ids=\${ids}`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    return {
      ...commonProps,
      name: model.name + '批量删除',
      url: baseUrl + '/batch',
      method: 'DELETE',
      responseType: 'UPDATE',
      // 使用预编译 #{ids} 防注入
      config: 'DELETE FROM ' + model.tableName + ' WHERE id IN (#{ids})',
    };
  },

  // ---- 3. 批量操作项 ----
  getAmisNode: (cap: CapabilityRow, _context: any): any => {
    const confirmText = cap.traitConfig?.confirmText || '确定要删除选中的数据吗？';
    return {
      label: cap.name,
      actionType: 'ajax',
      api: `${cap.apiMethod.toLowerCase()}:${getAmisApiPrefix()}${cap.apiPath}`,
      confirmText,
    };
  },
};

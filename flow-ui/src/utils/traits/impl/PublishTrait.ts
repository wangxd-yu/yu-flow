import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import { getAmisApiPrefix } from '../../env';
import type { DataModel } from '../../amisGenerator';

export const PublishTrait: FeatureTrait = {
  code: 'publish',
  name: '上下架状态切换',
  group: 'row-action',
  isDefault: false,

  // ── UI 约束元数据 ──
  /** 复合动作：包含上架与下架两种操作 */
  isComposite: true,
  /** 仅允许放置在行级操作区域 */
  allowedPositions: ['row'],
  /** 仅支持 AJAX 异步请求交互 */
  allowedInteractions: ['ajax'],

  // ── 配置表单结构 ──
  configSchema: [
    {
      name: 'statusField',
      label: '状态字段',
      type: 'field-select',
      defaultValue: 'status',
      required: true,
      tooltip: '选择用于标识上下架状态的字段',
    },
    {
      name: 'onlineValue',
      label: '上架值',
      type: 'input',
      defaultValue: '1',
      required: true,
      tooltip: '记录处于"上架/启用"状态时，该字段存储的值',
    },
    {
      name: 'offlineValue',
      label: '下架值',
      type: 'input',
      defaultValue: '0',
      required: true,
      tooltip: '记录处于"下架/停用"状态时，该字段存储的值',
    },
    {
      name: 'confirmText',
      label: '二次确认提示语',
      type: 'input',
      defaultValue: '确认切换状态?',
      required: false,
      tooltip: '操作前弹出的确认提示文案，留空则不弹出确认框',
    },
  ],

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'publish',
      name: '状态切换',
      method: 'PUT',
      url: `/dynamic/${moduleCode}/publish?id=\${id}`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    // 从 configSchema 读取默认值，PublishTrait 的 statusField 默认为 'status'
    const statusField = 'status';
    return {
      ...commonProps,
      name: model.name + '状态切换',
      url: baseUrl + '/publish',
      method: 'PUT',
      responseType: 'UPDATE',
      config: `UPDATE ${model.tableName} SET ${statusField} = \${${statusField}} WHERE id = \${id}`,
    };
  },

  // ---- 3. 动态 UI 生成（复合动作裂变） ----
  getAmisNode: (cap: CapabilityRow, _context: any): any => {
    const statusField = cap.traitConfig?.statusField || 'status';
    const enableValue = cap.traitConfig?.onlineValue || cap.traitConfig?.enableValue || '1';
    const disableValue = cap.traitConfig?.offlineValue || cap.traitConfig?.disableValue || '0';
    const confirmText = cap.traitConfig?.confirmText;

    const baseApiUrl = `${getAmisApiPrefix()}${cap.apiPath}`;
    const httpMethod = cap.apiMethod.toLowerCase();

    // 构建上架/启用按钮
    const onlineBtn = {
      type: 'button',
      level: 'link',
      actionType: 'ajax',
      label: '上架',
      visibleOn: `this.${statusField} == '${disableValue}'`,
      api: {
        method: httpMethod,
        url: baseApiUrl,
        data: {
          [statusField]: enableValue,
          '&': '$$',
        },
      },
      confirmText,
    };

    // 构建下架/停用按钮
    const offlineBtn = {
      type: 'button',
      level: 'link',
      className: 'text-danger',
      actionType: 'ajax',
      label: '下架',
      visibleOn: `this.${statusField} == '${enableValue}'`,
      api: {
        method: httpMethod,
        url: baseApiUrl,
        data: {
          [statusField]: disableValue,
          '&': '$$',
        },
      },
      confirmText,
    };

    return [onlineBtn, offlineBtn];
  },
};

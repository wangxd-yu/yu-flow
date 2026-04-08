import type { FeatureTrait, TraitApiItem, CapabilityRow } from '../types';
import { getAmisApiPrefix } from '../../env';
import type { DataModel } from '../../amisGenerator';

export const DetailQueryTrait: FeatureTrait = {
  code: 'detail',
  name: '详情查询',
  group: 'row-action',
  isDefault: true,

  // ── UI 约束元数据 ──
  allowedPositions: ['row'],
  allowedInteractions: ['drawer', 'dialog'],
  hideDataStrategy: true,

  // ---- 1. API 端点声明 ----
  injectApis: (moduleCode: string, _config: Record<string, any>): TraitApiItem[] => [
    {
      action: 'detail',
      name: '详情',
      method: 'GET',
      url: `/dynamic/${moduleCode}/detail?id=\${id}`,
    },
  ],

  // ---- 2. 后端 API 配置生成（含 SQL） ----
  generateBackendApi: (model: DataModel, commonProps: any, baseUrl: string): any => {
    return {
      ...commonProps,
      name: model.name + '详情',
      url: baseUrl + '/detail',
      method: 'GET',
      responseType: 'OBJECT',
      config: 'select * from ' + model.tableName + ' where id = ${id}',
    };
  },

  // ---- 3. 动态 UI 生成（详情查看按钮） ----
  getAmisNode: (cap: CapabilityRow, context: any): any => {
    const actionType = cap.interaction === 'drawer' ? 'drawer' : 'dialog';
    const formElement = {
      title: cap.name,
      ...(actionType === 'drawer' ? { position: 'right', size: 'lg' } : {}),
      body: {
        type: 'form',
        initApi: `${cap.apiMethod.toLowerCase()}:${getAmisApiPrefix()}${cap.apiPath}`,
        body: context.staticBody || [],
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

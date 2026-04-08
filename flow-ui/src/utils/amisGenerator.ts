/**
 * 数据模型转换为 Amis Schema 生成器（插件化版本）
 *
 * 通过遍历已注册的 FeatureTrait 插件的 getAmisToolbar / getAmisOperation / getAmisBulkAction 方法，
 * 动态拼装 Amis Page Schema，彻底消除硬编码的功能分支。
 */

import { getAmisApiPrefix } from './env';
import { TRAIT_LIST } from './traits';
import type { CapabilityRow } from './traits';

export interface FieldSchema {
  id?: string;
  fieldId: string;
  fieldName: string;
  dbType: string;
  uiType: string;
  isRequired: boolean;
  showInList: boolean;
  isSearchable: boolean;
  macroValue?: string;
  options?: { label: string; value: string; id?: string }[];
  validations?: string[];
  customRegexPattern?: string;
  maxLength?: number;
}

export interface DataModel {
  id: string;
  name: string;
  tableName: string;
  fieldsSchema?: string | FieldSchema[]; // JSON 字符串或数组
}

/**
 * 转换工厂函数
 *
 * @param model   模型定义
 * @param capabilities 页面包含的功能能力集合
 * @returns 完整的 Amis Page Schema
 */
export const generateAmisSchema = (model: any, capabilities: CapabilityRow[] = []) => {
  console.log('🚀 [Amis Generator] 传入的原始 Model:', model, 'capabilities:', capabilities);

  // 1. 兼容驼峰与下划线
  const rawSchema = model.fieldsSchema || model.fields_schema;

  let fields: any[] = [];
  try {
    if (typeof rawSchema === 'string') {
      fields = JSON.parse(rawSchema || '[]');
    } else if (Array.isArray(rawSchema)) {
      fields = rawSchema;
    }
  } catch (e) {
    console.error('❌ [Amis Generator] 解析 fields_schema 失败:', e);
  }

  console.log('📦 [Amis Generator] 最终解析出的 fields 数组:', fields);

  if (!fields || fields.length === 0) {
    console.warn('⚠️ 警告: fields 数组为空！页面将没有业务列。请检查是否在点击生成前调用了详情接口拉取最新数据！');
  }

  // 1. 构造新增/编辑表单 (formBody)
  const formBody: any[] = [];
  fields.forEach((field) => {
    const name = field.fieldId || field.name || field.enName;
    const label = field.fieldName || field.label || field.cnName;
    const uiType = field.uiType || field.type || 'input-text';

    if (name.toLowerCase() === 'id' || field.macroValue) {
      const hiddenItem: any = {
        type: 'hidden',
        name
      };
      // 将宏默认值透传给隐藏域，保证 Amis 提交时能携带系统字段值
      if (field.macroValue) {
        hiddenItem.value = field.macroValue;
      }
      formBody.push(hiddenItem);
      return;
    }

    const formItem: any = {
      type: uiType,
      name,
      label
    };

    if (field.maxLength) {
      formItem.maxLength = field.maxLength;
    }

    if (field.validations) {
      let valArray: any[] = [];
      if (Array.isArray(field.validations)) {
        valArray = field.validations;
      } else if (typeof field.validations === 'string') {
        try {
          valArray = JSON.parse(field.validations);
          if (!Array.isArray(valArray)) {
            valArray = [field.validations];
          }
        } catch (e) {
          valArray = [field.validations];
        }
      }

      if (valArray.length > 0) {
        const validationsObj: any = {};
        valArray.forEach((v: string) => {
          if (v === 'customRegex' && field.customRegexPattern) {
            validationsObj.matchRegexp = field.customRegexPattern;
          } else if (v !== 'customRegex') {
            validationsObj[v] = true;
          }
        });
        if (Object.keys(validationsObj).length > 0) {
          formItem.validations = validationsObj;
        }
      }
    }

    if (uiType === 'input-date') {
      formItem.format = 'YYYY-MM-DD';
      formItem.valueFormat = 'YYYY-MM-DD';
    } else if (uiType === 'input-datetime') {
      formItem.format = 'YYYY-MM-DD HH:mm:ss';
      formItem.valueFormat = 'YYYY-MM-DD HH:mm:ss';
    }

    if (field.isRequired) {
      formItem.required = true;
    }

    if (field.options && field.options.length > 0 && ['select', 'radio', 'radios', 'checkboxes'].includes(uiType)) {
      formItem.options = field.options;
    }

    formBody.push(formItem);
  });

  // 2. 构造详情弹窗 (staticBody)
  const staticBody: any[] = [];
  fields.forEach((field, index) => {
    const name = field.fieldId || field.name || field.enName;
    const label = field.fieldName || field.label || field.cnName;

    if (name.toLowerCase() === 'id') {
      return;
    }

    staticBody.push({
      type: 'static',
      name,
      label
    });

    // 插入分割线，除了最后一个元素外
    if (index < fields.length - 1) {
      staticBody.push({ type: 'divider' });
    }
  });

  // 3. 构造表格列 (columns)
  const columns: any[] = [];
  fields.forEach(field => {
    const name = field.fieldId || field.name || field.enName;
    const label = field.fieldName || field.label || field.cnName;
    const uiType = field.uiType || field.type || 'input-text';

    const showInList = field.showInList !== false;
    if (showInList && name.toLowerCase() !== 'id') {
      const col: any = { name, label };

      // 修复：枚举字段列表翻译
      if (field.options && field.options.length > 0) {
        col.type = 'mapping';
        const mapDict: Record<string, string> = {};
        field.options.forEach((opt: any) => {
          mapDict[opt.value] = opt.label;
        });
        // 兜底处理未知数据
        mapDict['*'] = '--';
        col.map = mapDict;
      }

      if (field.isSearchable) {
        const searchItem: any = { type: uiType, name, label, placeholder: `请输入${label}` };

        if (uiType === 'input-date') {
          searchItem.format = 'YYYY-MM-DD';
          searchItem.valueFormat = 'YYYY-MM-DD';
        } else if (uiType === 'input-datetime') {
          searchItem.format = 'YYYY-MM-DD HH:mm:ss';
          searchItem.valueFormat = 'YYYY-MM-DD HH:mm:ss';
        }

        if (field.options && field.options.length > 0 && ['select', 'radio', 'radios', 'checkboxes'].includes(uiType)) {
          searchItem.options = field.options;
        }
        col.searchable = searchItem;
      }
      columns.push(col);
    }
  });

  // ---- 插件化拼装：通过遍历 capabilities 动态收集 UI 片段 ----
  // 构造传递给 Trait 的上下文对象
  const context = { formBody, staticBody, fields, model };

  const toolbarButtons: any[] = [];
  const operationButtons: any[] = [];
  const bulkActions: any[] = [];

  for (const cap of capabilities) {
    // 寻找支撑该能力的 Trait 插件
    let node: any = null;
    if (cap.sourceTrait) {
      const trait = TRAIT_LIST.find((t) => t.code === cap.sourceTrait);
      if (trait && trait.getAmisNode) {
        node = trait.getAmisNode(cap, context);
      }
    }

    if (!node) continue;

    const nodes = Array.isArray(node) ? node : [node];

    for (const subNode of nodes) {
      // AOP 注入显隐条件
      if (cap.conditionType === 'conditional' && cap.conditionField) {
        const injectedCondition = `${cap.conditionField} ${cap.conditionOperator} '${cap.conditionValue}'`;
        if (subNode.visibleOn) {
          subNode.visibleOn = `\${(${subNode.visibleOn.replace(/^\$\{|\}$/g, '')}) && (${injectedCondition})}`;
        } else {
          subNode.visibleOn = `\${${injectedCondition}}`;
        }
      }

      // 动态分发布局位置
      if (cap.position === 'toolbar') {
        toolbarButtons.push(subNode);
      } else if (cap.position === 'row') {
        operationButtons.push(subNode);
      } else if (cap.position === 'batch') {
        bulkActions.push(subNode);
      }
      // inline 等其他 position 不在外部聚合，由组件自己在 columns 里处理，或者后续拓展...
    }
  }

  const finalColumns = [...columns];
  if (operationButtons.length > 0) {
    finalColumns.push({
      type: 'operation',
      label: '操作',
      width: 220,
      buttons: operationButtons
    });
  }

  // 组装最终的 Amis Schema
  const schema = {
    type: 'page',
    title: `${model.name}管理`,
    remark: '根据数据模型自动生成的页面',
    toolbar: toolbarButtons,
    body: {
      type: 'crud',
      draggable: true,
      api: {
        method: 'get',
        url: capabilities.find(c => c.sourceTrait === 'page')?.apiPath ? `${getAmisApiPrefix()}${capabilities.find(c => c.sourceTrait === 'page')?.apiPath}` : '',
        data: {
          page: `\${page - 1}`,
          size: `\${perPage}`,
          '&': '$$'
        }
      },
      perPage: 10,
      autoFillHeight: true,
      autoGenerateFilter: true,
      bulkActions,
      headerToolbar: [
        'bulkActions',
        { type: 'columns-toggler', align: 'right' },
        { type: 'drag-toggler', align: 'right' },
        { type: 'pagination', align: 'right' }
      ],
      footerToolbar: [
        'statistics',
        { type: 'pagination', layout: 'perPage,pager,go' }
      ],
      columns: finalColumns
    }
  };

  // 后置突变逻辑：允许插件直接对组装完成的 AST 进行改写
  for (const cap of capabilities) {
    if (cap.sourceTrait) {
      const trait = TRAIT_LIST.find((t) => t.code === cap.sourceTrait);
      if (trait && trait.mutateAmisSchema) {
        trait.mutateAmisSchema(schema, cap, context);
      }
    }
  }

  return schema;
};
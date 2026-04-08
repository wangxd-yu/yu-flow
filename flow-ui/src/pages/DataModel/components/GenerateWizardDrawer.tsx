/**
 * 生成页面与 API 配置向导 (Drawer) — V2 巨型配置表
 *
 * 设计理念：用一张 EditableProTable 聚合所有能力配置，
 * 消除信息割裂，让用户在同一视图内完成全部编排工作。
 *
 * 复合列实现方案：
 *   - API 接口列：通过 editable:false + render 实现 apiMethod + apiPath 双控件聚合
 *   - 按钮显示条件列：conditionType 选择 + 条件展开行
 *   - 扩展配置列：Popover 弹层编辑 trait configSchema
 *   - 防失焦：所有文本输入使用 StableInput（本地 state + blur 同步）
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  DrawerForm,
  ProFormText,
  ProFormSwitch,
  EditableProTable,
  ModalForm,
  ProFormRadio,
  ProFormList,
  ProFormGroup,
  ProFormSelect,
  ProFormCheckbox,
  ProFormDependency,
} from '@ant-design/pro-components';
import {
  Button,
  Checkbox,
  Col,
  Divider,
  Input,
  message,
  Popover,
  Row,
  Select,
  Space,
  Tag,
} from 'antd';
import type { FormInstance } from 'antd';
import { request } from '@umijs/max';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';

import { generateAmisSchema, DataModel } from '@/utils/amisGenerator';
import { generateCrudApis } from '@/utils/apiGenerator';
import { addPage } from '@/pages/PageManage/services/pageManage';
import { TRAIT_LIST } from '@/utils/traits';
import type { FeatureTrait, TraitConfigItem } from '@/utils/traits';

// ================================================================
// 常量 & 类型
// ================================================================

const POSITION_ENUM: Record<string, { text: string }> = {
  toolbar: { text: '工具栏' },
  row: { text: '行级操作' },
  batch: { text: '批量操作' },
  inline: { text: '列内操作' },
};

const INTERACTION_ENUM: Record<string, { text: string }> = {
  dialog: { text: '弹窗' },
  drawer: { text: '抽屉' },
  ajax: { text: '直接请求' },
  local: { text: '无交互' },
  page: { text: '跳转页面' },
};

const STRATEGY_ENUM: Record<string, { text: string }> = {
  generate: { text: '生成新接口' },
  bind: { text: '绑定已有' },
  localData: { text: '当前行数据' },
};

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE'].map((m) => ({ label: m, value: m }));

const CONDITION_TYPE_OPTIONS = [
  { label: '始终显示', value: 'always' },
  { label: '条件显示', value: 'conditional' },
];

const OPERATOR_OPTIONS = ['==', '!=', '>', '<'].map((o) => ({ label: o, value: o }));

/** 能力行数据结构 */
interface CapabilityRow {
  id: string;
  name: string;
  position: 'toolbar' | 'row' | 'batch' | 'inline';
  interaction: 'dialog' | 'drawer' | 'ajax' | 'local' | 'page';
  dataStrategy: 'generate' | 'bind' | 'localData';
  apiMethod: 'GET' | 'POST' | 'PUT' | 'DELETE';
  apiPath: string;
  conditionType: 'always' | 'conditional';
  conditionField: string;
  conditionOperator: string;
  conditionValue: string;
  sourceTrait?: string;
  traitConfig?: Record<string, any>;
}

// ================================================================
// 工具函数
// ================================================================

function tableNameToCamelCase(tableName: string): string {
  if (!tableName) return '';
  const parts = tableName.split('_');
  const words = parts.length > 1 ? parts.slice(1) : parts;
  return words
    .map((word, i) => (i === 0 ? word.toLowerCase() : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()))
    .join('');
}

const ALL_CRUD_TRAITS = ['page', 'detail', 'insert', 'update', 'delete', 'batch-delete'];
const READONLY_TRAITS = ['page', 'detail'];

/** 分组配置，前端按此顺序渲染 */
const GROUP_CONFIG = [
  { key: 'toolbar', title: '工具栏' },
  { key: 'query', title: '条件查询' },
  { key: 'row-action', title: '行级操作' },
  { key: 'column-action', title: '列级操作' },
  { key: 'advanced', title: '高级特性' },
] as const;

const getTraitsByTemplate = (template?: string): string[] => {
  if (template === 'readonly') return [...READONLY_TRAITS];
  return [...ALL_CRUD_TRAITS];
};

/** Trait → 能力行的默认属性映射 */
const TRAIT_DEFAULTS: Record<string, Partial<CapabilityRow>> = {
  page: { position: 'toolbar', interaction: 'local', dataStrategy: 'generate', apiMethod: 'GET' },
  detail: { position: 'row', interaction: 'dialog', dataStrategy: 'localData', apiMethod: 'GET' },
  insert: { position: 'toolbar', interaction: 'drawer', dataStrategy: 'generate', apiMethod: 'POST' },
  update: { position: 'row', interaction: 'dialog', dataStrategy: 'generate', apiMethod: 'PUT' },
  delete: { position: 'row', interaction: 'ajax', dataStrategy: 'generate', apiMethod: 'DELETE' },
  'batch-delete': { position: 'batch', interaction: 'ajax', dataStrategy: 'generate', apiMethod: 'DELETE' },
  publish: {
    position: 'row', interaction: 'ajax', dataStrategy: 'generate', apiMethod: 'PUT',
    conditionType: 'conditional', conditionField: 'status', conditionOperator: '==', conditionValue: '0'
  },
};

let idSeq = 1;

/** 将勾选的 traits 映射为能力行 */
const mapTraitsToCapabilities = (
  traits: string[],
  moduleCode: string,
  moduleName: string,
): CapabilityRow[] => {
  const rows: CapabilityRow[] = [];
  for (const code of traits) {
    const trait = TRAIT_LIST.find((t) => t.code === code);
    if (!trait) continue;

    // 1. 读取 Trait 扩展配置的默认值
    const defaultCfg: Record<string, any> = {};
    trait.configSchema?.forEach((f) => { defaultCfg[f.name] = f.defaultValue; });

    // 2. 读取 Trait 声明的 API 接口
    const apis = trait.injectApis(moduleCode || '', defaultCfg);
    const firstApi = apis[0];

    // 3. 读取极少数的业务强关联预设（如 PublishTrait 上下架需要默认配条件状态）
    const defs = TRAIT_DEFAULTS[code] || {};

    rows.push({
      id: `cap_${idSeq++}`,
      name: trait.name,
      // ✨ 核心净化：严格从底层协议 trait.allowedPositions / allowedInteractions 中推导！
      position: (defs.position as any) || trait.allowedPositions?.[0] || 'row',
      interaction: (defs.interaction as any) || trait.allowedInteractions?.[0] || 'dialog',

      // 策略与接口也从元数据推导
      dataStrategy: (defs.dataStrategy as any) || (trait.hideDataStrategy ? 'localData' : 'generate'),
      apiMethod: firstApi?.method || (defs.apiMethod as any) || 'GET',
      apiPath: firstApi?.url || '',

      conditionType: (defs.conditionType as any) || 'always',
      conditionField: defs.conditionField || '',
      conditionOperator: defs.conditionOperator || '==',
      conditionValue: defs.conditionValue || '',
      sourceTrait: code,
      traitConfig: { ...defaultCfg },
    });
  }
  return rows;
};

// ================================================================
// 防抖输入框（避免受控表格失焦）
// ================================================================

const StableInput: React.FC<{
  value?: string;
  onChange?: (v: string) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}> = ({ value: propValue, onChange, placeholder, style }) => {
  const [local, setLocal] = useState(propValue || '');
  const composing = useRef(false);
  useEffect(() => { if (!composing.current) setLocal(propValue || ''); }, [propValue]);
  return (
    <Input
      size="small"
      value={local}
      placeholder={placeholder}
      style={style}
      autoComplete="off"
      onChange={(e) => { setLocal(e.target.value); if (!composing.current) onChange?.(e.target.value); }}
      onCompositionStart={() => { composing.current = true; }}
      onCompositionEnd={(e: any) => { composing.current = false; onChange?.(e.target.value); }}
      onBlur={() => onChange?.(local)}
    />
  );
};

// ================================================================
// 复合单元格子组件
// ================================================================

/** API 接口复合单元格 (apiMethod + apiPath) */
const ApiCompositeCell: React.FC<{
  record: CapabilityRow;
  onPatch: (id: string, p: Partial<CapabilityRow>) => void;
}> = React.memo(({ record, onPatch }) => {
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (record.dataStrategy !== 'generate' || !record.apiPath) {
      setErrorMsg(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await request<any>('/flow-api/api/check-exact', {
          method: 'GET',
          params: { method: record.apiMethod, url: record.apiPath },
        });
        if (res?.data === true || res === true) {
          setErrorMsg('接口路径已存在/冲突');
        } else {
          setErrorMsg(null);
        }
      } catch (e: any) {
        setErrorMsg('接口路径已存在/冲突');
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [record.apiMethod, record.apiPath, record.dataStrategy]);

  if (record.dataStrategy === 'localData') {
    return (
      <span style={{ color: '#bbb', fontSize: 12, fontStyle: 'italic', display: 'block', textAlign: 'center' }}>
        — 无需 API —
      </span>
    );
  }

  return (
    <Popover content={errorMsg} open={!!errorMsg} placement="topLeft" overlayInnerStyle={{ color: '#ff4d4f' }}>
      <Space.Compact style={{ width: '100%', ...(errorMsg ? { border: '1px solid #ff4d4f', borderRadius: 6 } : {}) }}>
        <Select
          value={record.apiMethod}
          onChange={(v) => onPatch(record.id, { apiMethod: v })}
          options={METHOD_OPTIONS}
          style={{ width: 88 }}
        />
        <StableInput
          value={record.apiPath}
          onChange={(v) => onPatch(record.id, { apiPath: v })}
          placeholder="/dynamic/..."
          style={{ flex: 1 }}
        />
      </Space.Compact>
    </Popover>
  );
});

/** 按钮显示条件复合单元格 */
const ConditionCompositeCell: React.FC<{
  record: CapabilityRow;
  fieldOptions: { label: string; value: string }[];
  onPatch: (id: string, p: Partial<CapabilityRow>) => void;
}> = React.memo(({ record, fieldOptions, onPatch }) => (
  <div>
    <Select
      size="small"
      value={record.conditionType}
      onChange={(v) => onPatch(record.id, { conditionType: v })}
      options={CONDITION_TYPE_OPTIONS}
      style={{ width: '100%' }}
    />
    {record.conditionType === 'conditional' && (
      <div style={{ display: 'flex', gap: 4, marginTop: 6 }}>
        <Select
          size="small"
          value={record.conditionField || undefined}
          onChange={(v) => onPatch(record.id, { conditionField: v })}
          options={fieldOptions}
          placeholder="字段"
          showSearch
          filterOption={(input, opt) => (opt?.label as string ?? '').toLowerCase().includes(input.toLowerCase())}
          style={{ flex: 1, minWidth: 0 }}
        />
        <Select
          size="small"
          value={record.conditionOperator || '=='}
          onChange={(v) => onPatch(record.id, { conditionOperator: v })}
          options={OPERATOR_OPTIONS}
          style={{ width: 58 }}
        />
        <StableInput
          value={record.conditionValue}
          onChange={(v) => onPatch(record.id, { conditionValue: v })}
          placeholder="值"
          style={{ width: 60 }}
        />
      </div>
    )}
  </div>
));

// ================================================================
// TraitConfigModal (Metadata-Driven)
// ================================================================

/**
 * parseVisibleOn: extract dependency field and evaluator from visibleOn expression.
 * Supported formats: "fieldName === value" / "fieldName == value" / "fieldName !== value"
 */
function parseVisibleOn(expr: string): { depName: string; evaluate: (val: any) => boolean } | null {
  const m = expr.match(/^\s*(\w+)\s*(===?|!==?)\s*(.+)\s*$/);
  if (!m) return null;
  const [, depName, op, rawVal] = m;
  let expected: any = rawVal.trim();
  if (expected === 'true') expected = true;
  else if (expected === 'false') expected = false;
  else if (/^\d+(\.\d+)?$/.test(expected)) expected = Number(expected);
  else expected = expected.replace(/^['"]|['"]$/g, '');

  const evaluate = (val: any) => {
    if (op === '===' || op === '==') return val === expected;
    if (op === '!==' || op === '!=') return val !== expected;
    return true;
  };
  return { depName, evaluate };
}

/** Render a single form control based on TraitConfigItem metadata */
const renderConfigField = (
  item: TraitConfigItem,
  fieldOptions: { label: string; value: string }[],
) => {
  const commonProps: Record<string, any> = {
    name: item.name,
    label: item.label,
    tooltip: item.tooltip,
    rules: item.required ? [{ required: true, message: `${item.label} is required` }] : undefined,
  };

  switch (item.type) {
    case 'input':
      return <ProFormText {...commonProps} placeholder={`Enter ${item.label}`} />;
    case 'field-select':
      return <ProFormSelect {...commonProps} options={fieldOptions} showSearch placeholder={`Select ${item.label}`} />;
    case 'field-select-multiple':
      return <ProFormSelect {...commonProps} mode="multiple" options={fieldOptions} placeholder={`Select ${item.label}`} />;
    case 'select':
      return <ProFormSelect {...commonProps} options={item.options || []} placeholder={`Select ${item.label}`} />;
    case 'switch':
      return <ProFormSwitch {...commonProps} />;
    default:
      return <ProFormText {...commonProps} />;
  }
};

/** Universal metadata-driven config modal */
const TraitConfigModal: React.FC<{
  record: CapabilityRow;
  trait: FeatureTrait;
  fieldOptions: { label: string; value: string }[];
  onPatch: (id: string, patch: Partial<CapabilityRow>) => void;
}> = ({ record, trait, fieldOptions, onPatch }) => {
  const schema = trait.configSchema || [];
  const cfg = record.traitConfig || {};
  const hasConfig = schema.some((f) => cfg[f.name] !== undefined && cfg[f.name] !== f.defaultValue);

  const initialValues = useMemo(() => {
    const vals: Record<string, any> = {};
    schema.forEach((f) => {
      vals[f.name] = cfg[f.name] !== undefined ? cfg[f.name] : f.defaultValue;
    });
    return vals;
  }, [schema, cfg]);

  return (
    <ModalForm
      title={`\u2699\uFE0F [${record.name}] 参数配置`}
      width={560}
      trigger={
        <a style={{ whiteSpace: 'nowrap', fontSize: 13 }}>
          {hasConfig ? '\u2705 已配置' : '\u2699\uFE0F 参数配置'}
        </a>
      }
      modalProps={{ destroyOnClose: true, maskClosable: false }}
      initialValues={initialValues}
      onFinish={async (formValues) => {
        onPatch(record.id, { traitConfig: { ...cfg, ...formValues } });
        message.success('保存成功');
        return true;
      }}
      submitter={{ searchConfig: { submitText: '保存', resetText: '取消' } }}
    >
      {schema.map((item) => {
        const fieldNode = renderConfigField(item, fieldOptions);
        if (!item.visibleOn) return <React.Fragment key={item.name}>{fieldNode}</React.Fragment>;

        const parsed = parseVisibleOn(item.visibleOn);
        if (!parsed) return <React.Fragment key={item.name}>{fieldNode}</React.Fragment>;

        return (
          <ProFormDependency key={item.name} name={[parsed.depName]}>
            {(depValues: Record<string, any>) => {
              if (!parsed.evaluate(depValues[parsed.depName])) return null;
              return fieldNode;
            }}
          </ProFormDependency>
        );
      })}
    </ModalForm>
  );
};

// ================================================================
// 主组件
// ================================================================

export interface GenerateWizardDrawerProps {
  open: boolean;
  onOpenChange: (visible: boolean) => void;
  model: any;
  templateId?: 'crud' | 'readonly' | 'custom';
  onSuccess?: (pageId: string) => void;
}

const GenerateWizardDrawer: React.FC<GenerateWizardDrawerProps> = ({
  open,
  onOpenChange,
  model,
  templateId,
  onSuccess,
}) => {
  const formRef = useRef<FormInstance>();

  // ---- 能力表数据 (state-controlled) ----
  const [capabilities, setCapabilities] = useState<CapabilityRow[]>([]);
  const [editableKeys, setEditableKeys] = useState<React.Key[]>([]);

  // ---- 字段选项 ----
  const fieldOptions = useMemo(() => {
    const raw = model?.fieldsSchema || model?.fields_schema;
    let fields: any[] = [];
    try {
      if (typeof raw === 'string') fields = JSON.parse(raw || '[]');
      else if (Array.isArray(raw)) fields = raw;
    } catch { /* ignore */ }
    return fields.map((f: any) => ({
      label: `${f.fieldName || f.fieldId} (${f.fieldId})`,
      value: f.fieldId || f.name || f.enName,
    }));
  }, [model]);

  const initialModuleCode = tableNameToCamelCase(model?.tableName || '');

  const [selectedTraits, setSelectedTraits] = useState<string[]>([]);

  // ---- 初始化 ----
  useEffect(() => {
    if (!open) return;
    const initialTraits = getTraitsByTemplate(templateId);
    setSelectedTraits(initialTraits);

    const caps = mapTraitsToCapabilities(initialTraits, initialModuleCode, model?.name || '');
    setCapabilities(caps);
    setEditableKeys(caps.map((c) => c.id));

    setTimeout(() => {
      formRef.current?.resetFields();
      formRef.current?.setFieldsValue({
        moduleName: model?.name || '',
        moduleCode: initialModuleCode,
        pageName: model?.name ? `${model.name}管理` : '',
        pagePath: initialModuleCode ? `/pages/${initialModuleCode}` : '',
        directoryId: model?.directoryId || undefined,
      });
      if (initialModuleCode) {
        formRef.current?.validateFields(['pagePath']).catch(() => { });
      }
    }, 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, model, templateId]);

  // ---- 功能勾选变更 ----
  const handleTraitChange = (checked: boolean, traitCode: string) => {
    const nextTraits = checked
      ? [...selectedTraits, traitCode]
      : selectedTraits.filter((t) => t !== traitCode);
    setSelectedTraits(nextTraits);

    if (checked) {
      // Add new capability row
      const mc = formRef.current?.getFieldValue('moduleCode') || initialModuleCode;
      const mn = formRef.current?.getFieldValue('moduleName') || model?.name || '';
      const newCaps = mapTraitsToCapabilities([traitCode], mc, mn);
      setCapabilities((prev) => [...prev, ...newCaps]);
      setEditableKeys((prev) => [...prev, ...newCaps.map((c) => c.id)]);
    } else {
      // Remove capability row(s) belonging to this trait
      setCapabilities((prev) => prev.filter((c) => c.sourceTrait !== traitCode));
      setEditableKeys((prev) => prev.filter((k) => {
        const cap = capabilities.find(c => c.id === k);
        return !(cap && cap.sourceTrait === traitCode);
      }));
    }
  };

  // ---- 行数据更新 ----
  const patchRow = useCallback((id: string, patch: Partial<CapabilityRow>) => {
    setCapabilities((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
  }, []);

  // ---- moduleCode / moduleName 联动重算 API 路径和名称 ----
  const handleValuesChange = (changed: Record<string, any>) => {
    const patches: Record<string, any> = {};
    if (changed.moduleName !== undefined) {
      patches.pageName = changed.moduleName ? `${changed.moduleName}管理` : '';
    }
    if (changed.moduleCode !== undefined) {
      patches.pagePath = changed.moduleCode ? `/pages/${changed.moduleCode}` : '';
    }
    if (Object.keys(patches).length) formRef.current?.setFieldsValue(patches);
    if (patches.pagePath) {
      formRef.current?.validateFields(['pagePath']).catch(() => { });
    }

    // 重算能力表中的 apiPath 和 name
    if (changed.moduleCode !== undefined || changed.moduleName !== undefined) {
      const mc = changed.moduleCode ?? formRef.current?.getFieldValue('moduleCode') ?? '';
      setCapabilities((prev) =>
        prev.map((row) => {
          if (!row.sourceTrait) return row;
          const trait = TRAIT_LIST.find((t) => t.code === row.sourceTrait);
          if (!trait) return row;
          const apis = trait.injectApis(mc, row.traitConfig || {});
          const first = apis[0];
          const nextRow = { ...row };
          if (changed.moduleCode !== undefined && first) nextRow.apiPath = first.url;
          if (changed.moduleName !== undefined) nextRow.name = trait.name;
          return nextRow;
        }),
      );
    }
  };

  // ---- 校验：页面路径查重 ----
  const checkPagePath = async (_: any, value: string) => {
    if (!value) return;
    try {
      const res = await request<any>('/flow-api/pages/check-path', { method: 'GET', params: { path: value } });
      if (res?.data === true || res === true) throw new Error('路径冲突');
    } catch (e: any) {
      if (e?.message === '路径冲突') return Promise.reject(e);
    }
  };

  // ---- 添加自定义能力行 ----
  const handleAddCapability = () => {
    const newId = `cap_${idSeq++}`;
    const row: CapabilityRow = {
      id: newId,
      name: '自定义操作',
      position: 'row',
      interaction: 'ajax',
      dataStrategy: 'generate',
      apiMethod: 'POST',
      apiPath: '',
      conditionType: 'always',
      conditionField: '',
      conditionOperator: '==',
      conditionValue: '',
    };
    setCapabilities((prev) => [...prev, row]);
    setEditableKeys((prev) => [...prev, newId]);
  };

  // ---- 提交 ----
  const handleFinish = async (values: Record<string, any>) => {
    const { pageName, pagePath, directoryId, moduleCode } = values;
    const activeCaps = capabilities;
    const traits = [...new Set(activeCaps.map((c) => c.sourceTrait).filter(Boolean))] as string[];

    // 1) 批量创建 API
    const hideApi = message.loading('正在批量创建 API...', 0);
    try {
      const finalApis = generateCrudApis(
        model as DataModel,
        directoryId || '',
        activeCaps,
        model.datasource || '[DEFAULT]',
        `/dynamic/${moduleCode}`
      );
      finalApis.forEach((api: any) => {
        delete api._traitCode;
      });
      await request('/flow-api/api/batch/create', { method: 'POST', data: finalApis });
    } catch (err: any) {
      hideApi();
      message.error('批量创建 API 失败：' + (err?.message || ''));
      return false;
    }
    hideApi();
    message.success('API 批量创建成功');

    // 2) 生成 Amis 页面
    const schema = generateAmisSchema(model as DataModel, activeCaps);
    try {
      const res = await addPage({
        name: pageName,
        routePath: pagePath,
        json: JSON.stringify(schema),
        status: 0,
        directoryId: directoryId || null,
      } as any);
      message.success('生成页面成功！');
      const newPageId = res?.data?.id || res?.id;
      if (newPageId && onSuccess) onSuccess(newPageId);
    } catch (e: any) {
      message.error('保存页面失败：' + (e?.message || ''));
      return false;
    }
    return true;
  };

  // ---- columns (Metadata-Driven) ----
  const columns: any[] = [
    {
      title: '功能区域',
      dataIndex: 'position',
      valueType: 'select',
      width: 100,
      valueEnum: POSITION_ENUM,
      renderFormItem: (_: any, config: any) => {
        const record: CapabilityRow = config.record || config.entry;
        if (!record) return null;
        const trait = TRAIT_LIST.find((t) => t.code === record.sourceTrait);
        const allowed = trait?.allowedPositions;
        const allOpts = Object.entries(POSITION_ENUM).map(([k, v]) => ({ label: v.text, value: k }));
        const opts = allowed ? allOpts.filter((o) => allowed.includes(o.value as any)) : allOpts;
        const isSingle = opts.length === 1;
        return (
          <Select
            size="small"
            value={record.position}
            onChange={(v) => patchRow(record.id, { position: v })}
            options={opts}
            disabled={isSingle}
            style={{ width: '100%' }}
          />
        );
      },
    },
    {
      title: '能力名称',
      dataIndex: 'name',
      width: 130,
      formItemProps: { rules: [{ required: true, message: '必填' }] },
    },
    {
      title: '交互方式',
      dataIndex: 'interaction',
      valueType: 'select',
      width: 100,
      valueEnum: INTERACTION_ENUM,
      renderFormItem: (_: any, config: any) => {
        const record: CapabilityRow = config.record || config.entry;
        if (!record) return null;
        const trait = TRAIT_LIST.find((t) => t.code === record.sourceTrait);
        const allowed = trait?.allowedInteractions;
        const allOpts = Object.entries(INTERACTION_ENUM).map(([k, v]) => ({ label: v.text, value: k }));
        const opts = allowed ? allOpts.filter((o) => allowed.includes(o.value as any)) : allOpts;
        const isSingle = opts.length === 1;
        return (
          <Select
            size="small"
            value={record.interaction}
            onChange={(v) => patchRow(record.id, { interaction: v })}
            options={opts}
            disabled={isSingle}
            style={{ width: '100%' }}
          />
        );
      },
    },
    {
      title: '接口策略',
      dataIndex: 'dataStrategy',
      valueType: 'select',
      width: 115,
      valueEnum: STRATEGY_ENUM,
    },
    {
      title: 'API 接口',
      key: 'apiComposite',
      width: 260,
      render: (_: any, record: CapabilityRow) => {
        if (record.dataStrategy === 'localData') {
          return <Tag bordered={false} color="default">🚫 无需接口</Tag>;
        }
        if (record.dataStrategy === 'bind') {
          return <span style={{ color: '#888' }}>[绑定系统接口]</span>;
        }
        return <span>{record.apiMethod} {record.apiPath}</span>;
      },
      renderFormItem: (_: any, config: any) => {
        const record = config.record || config.entry;
        if (!record) return null;
        if (record.dataStrategy === 'localData') {
          return <Tag bordered={false} color="default" style={{ margin: '5px 0' }}>🚫 无需接口</Tag>;
        }
        if (record.dataStrategy === 'bind') {
          return <Select showSearch placeholder="搜索并绑定系统接口" />;
        }
        return <ApiCompositeCell record={record} onPatch={patchRow} />;
      },
    },
    {
      title: '显示条件',
      key: 'conditionComposite',
      width: 210,
      render: (_: any, record: CapabilityRow) => {
        if (record.conditionType === 'always') {
          return <span style={{ color: '#bfbfbf', fontSize: 13 }}>无限制</span>;
        }
        return <span style={{ fontSize: 13 }}>{`${record.conditionField || ''} ${record.conditionOperator || '=='} ${record.conditionValue || ''}`}</span>;
      },
      renderFormItem: (_: any, config: any) => {
        const record = config.record || config.entry;
        if (!record) return null;
        return <ConditionCompositeCell record={record} fieldOptions={fieldOptions} onPatch={patchRow} />;
      },
    },
    // ---- extensionConfig: TraitConfigModal ----
    {
      title: '扩展配置',
      key: 'extensionConfig',
      width: 110,
      editable: false,
      align: 'center' as const,
      render: (_: any, record: CapabilityRow) => {
        const trait = TRAIT_LIST.find((t) => t.code === record.sourceTrait);

        // Has configSchema -> universal TraitConfigModal
        if (trait?.configSchema?.length) {
          return (
            <TraitConfigModal
              record={record}
              trait={trait}
              fieldOptions={fieldOptions}
              onPatch={patchRow}
            />
          );
        }

        // No config
        return <span style={{ color: '#ccc', fontSize: 12 }}>-- none --</span>;
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 60,
      render: (_: any, record: CapabilityRow) => (
        <Button
          type="link"
          danger
          size="small"
          onClick={() => {
            setCapabilities((prev) => prev.filter((r) => r.id !== record.id));
            setEditableKeys((prev) => prev.filter((k) => k !== record.id));
          }}
        >
          删除
        </Button>
      ),
    },
  ];

  return (
    <DrawerForm
      title="⚡ 页面能力编排向导"
      width="88vw"
      layout="horizontal"
      labelCol={{ span: 4 }}
      wrapperCol={{ span: 20 }}
      grid
      rowProps={{ gutter: [16, 0] }}
      open={open}
      onOpenChange={onOpenChange}
      formRef={formRef}
      initialValues={{
        moduleName: model?.name || '',
        moduleCode: initialModuleCode,
        pageName: model?.name ? `${model.name}管理` : '',
        pagePath: initialModuleCode ? `/pages/${initialModuleCode}` : '',
        directoryId: model?.directoryId || undefined,
      }}
      drawerProps={{ destroyOnClose: true, maskClosable: false }}
      onValuesChange={handleValuesChange}
      onFinish={handleFinish}
      submitter={{ searchConfig: { submitText: '确认生成', resetText: '取消' } }}
    >
      {/* ========== 基础信息区 ========== */}
      <ProFormText
        name="moduleName"
        label="模块名称"
        colProps={{ span: 12 }}
        formItemProps={{ style: { marginBottom: 10 } }}
        rules={[{ required: true, message: '模块名称为必填项' }]}
        placeholder="例如：学生"
        tooltip="修改后将联动更新页面名称及能力表中的名称"
        fieldProps={{ autoComplete: 'off' }}
      />
      <ProFormText
        name="moduleCode"
        label="模块编码"
        colProps={{ span: 12 }}
        formItemProps={{ style: { marginBottom: 10 } }}
        rules={[{ required: true, message: '模块编码为必填项' }]}
        placeholder="例如：student"
        tooltip="修改后将联动更新页面路径及 API 路径"
        fieldProps={{ autoComplete: 'off' }}
      />
      <DirectoryTreeSelect
        name="directoryId"
        label="目标目录"
        colProps={{ span: 12 }}
        formItemProps={{ style: { marginBottom: 10 } }}
        rules={[{ required: true, message: '请选择目标目录' }]}
        placeholder="请选择生成内容的目标目录"
      />
      <ProFormText
        name="pageName"
        label="页面名称"
        colProps={{ span: 12 }}
        formItemProps={{ style: { marginBottom: 10 } }}
        rules={[{ required: true, message: '页面名称为必填项' }]}
        placeholder="例如：学生管理"
        fieldProps={{ autoComplete: 'off' }}
      />
      <ProFormText
        name="pagePath"
        label="页面路径"
        colProps={{ span: 12 }}
        formItemProps={{ style: { marginBottom: 10 } }}
        validateTrigger="onBlur"
        rules={[
          { required: true, message: '页面路径为必填项' },
          { pattern: /^\/[a-zA-Z0-9_\-/]+$/, message: '路径必须以 / 开头' },
          { validator: checkPagePath },
        ]}
        fieldProps={{ autoComplete: 'off' }}
      />

      {/* ========== 功能插件勾选区 ========== */}
      <Divider orientation="left" style={{ fontSize: 13, color: '#888', marginTop: 0 }}>
        第一步：启用页面能力 (勾选需要的原子功能)
      </Divider>
      <div style={{ padding: '0 24px', marginBottom: 0 }}>
        <Checkbox.Group value={selectedTraits} style={{ width: '100%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0px', width: '100%' }}>
            {GROUP_CONFIG.map((group) => {
              const traitsInGroup = TRAIT_LIST.filter((t) => t.group === group.key);
              if (traitsInGroup.length === 0) return null;

              return (
                <Row key={group.key} wrap={false} align="top">
                  <Col flex="80px" style={{ textAlign: 'right', paddingRight: 16, color: '#595959', lineHeight: '32px' }}>
                    {group.title}:
                  </Col>
                  <Col flex="auto">
                    <Space size={[24, 12]} wrap style={{ lineHeight: '32px' }}>
                      {traitsInGroup.map((trait) => (
                        <Checkbox
                          key={trait.code}
                          value={trait.code}
                          onChange={(e) => handleTraitChange(e.target.checked, trait.code)}
                        >
                          {trait.name}
                        </Checkbox>
                      ))}
                    </Space>
                  </Col>
                </Row>
              );
            })}
          </div>
        </Checkbox.Group>
      </div>

      {/* ========== 能力编排表 ========== */}
      <Divider orientation="left" style={{ fontSize: 13, color: '#888' }}>
        第二步：页面能力编排表 (展示并可自定义配置功能)
      </Divider>

      <EditableProTable<CapabilityRow>
        rowKey="id"
        columns={columns}
        value={capabilities}
        onChange={(v) => setCapabilities([...v])}
        recordCreatorProps={false}
        editable={{
          type: 'multiple',
          editableKeys,
          onChange: setEditableKeys,
          actionRender: () => [],
        }}
        scroll={{ x: 1100 }}
        size="small"
        style={{ width: '100%' }}
        toolBarRender={false}
      />
    </DrawerForm>
  );
};

export default GenerateWizardDrawer;

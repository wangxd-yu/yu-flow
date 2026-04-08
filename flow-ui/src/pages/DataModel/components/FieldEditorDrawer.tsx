import React, { useState, useEffect, useContext, useRef } from 'react';
import { Drawer, Button, Space, message, Select, Input, InputNumber, Dropdown, Modal, AutoComplete } from 'antd';
import type { MenuProps } from 'antd';
import { EditableProTable, ProColumns, ModalForm, ProFormTextArea, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import type { EditableFormInstance } from '@ant-design/pro-components';
import { HolderOutlined, DownOutlined } from '@ant-design/icons';
import { getModelDetail, updateModel } from '../services/dataModel';
import { queryDataSourceList } from '@/pages/flow/dataSource/services/dataSource';
import { request } from '@umijs/max';

// ── dnd-kit ─────────────────────────────────────────────────
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

interface FieldSchema {
  id: string; // 唯一标识，前端本地生成用于 EditableProTable 行 key
  fieldId: string; // 字段英文名
  fieldName: string; // 字段中文名
  dbType: string; // DB 类型
  uiType: string; // UI 组件类型
  isRequired: boolean;
  showInList: boolean;
  isSearchable: boolean;
  isPrimaryKey: boolean;
  maxLength?: number;
  validations?: string[];
  customRegexPattern?: string;
  macroValue?: string;
  options?: { label: string; value: string; id?: string }[];
}

interface FieldEditorDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  modelId: string;
  onRefresh?: () => void;
}

// ═══════════════════════════════════════════════════════════════
// 可拖拽排序行组件
// ═══════════════════════════════════════════════════════════════
const DragHandleContext = React.createContext<Record<string, any>>({});

const SortableRow = (props: any) => {
  const { 'data-row-key': rowKey, ...restProps } = props;
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: rowKey as string,
    transition: { duration: 200, easing: 'ease' }
  });

  const style: React.CSSProperties = {
    ...restProps.style,
    transform: transform ? `translateY(${Math.round(transform.y)}px)` : undefined,
    transition,
    ...(isDragging ? { position: 'relative', zIndex: 9999, opacity: 0.85, boxShadow: '0 2px 10px rgba(0,0,0,0.1)' } : {}),
  };

  return (
    <DragHandleContext.Provider value={listeners ?? {}}>
      <tr {...restProps} ref={setNodeRef} style={style} {...attributes} />
    </DragHandleContext.Provider>
  );
};

const DragHandle: React.FC = () => {
  const listeners = useContext(DragHandleContext);
  return (
    <HolderOutlined
      {...listeners}
      style={{ cursor: 'grab', color: '#bfbfbf', fontSize: 14, marginRight: 8 }}
    />
  );
};

// ═══════════════════════════════════════════════════════════════
// 主组件
// ═══════════════════════════════════════════════════════════════
const FieldEditorDrawer: React.FC<FieldEditorDrawerProps> = ({ open, onOpenChange, modelId, onRefresh }) => {
  const [loading, setLoading] = useState(false);
  const [modelBaseInfo, setModelBaseInfo] = useState<any>(null);

  // 用于管理的 dataSource
  const [dataSource, setDataSource] = useState<FieldSchema[]>([]);
  // 记录正在编辑的 keys
  const [editableKeys, setEditableKeys] = useState<React.Key[]>([]);
  // EditableProTable 内部 form 实例（用于跨行更新 form 值）
  const editableFormRef = useRef<EditableFormInstance<FieldSchema>>();

  // 选项配置弹窗状态
  const [optModalData, setOptModalData] = useState<{ rowKey: string; form: any; options: any[] } | null>(null);
  const [optEditableKeys, setOptEditableKeys] = useState<React.Key[]>([]);

  // ---- 快捷导入弹窗状态 ----
  const [ddlModalVisible, setDdlModalVisible] = useState(false);
  const [dbModalVisible, setDbModalVisible] = useState(false);

  // ---- 合并数据逻辑 ----
  const handleMergeFields = (newFields: FieldSchema[]) => {
    if (!newFields || newFields.length === 0) {
      message.warning('获取到的字段内容为空');
      return;
    }

    const prepareFields = (fields: any[]) => {
      return fields.map((item) => {
        const fieldEngName = item.fieldId || item.name || item.enName || '';
        const isId = fieldEngName.toLowerCase() === 'id';
        return {
          ...item,
          id: `field_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
          fieldId: fieldEngName,
          showInList: item.showInList ?? true,
          isSearchable: item.isSearchable ?? false,
          isRequired: item.isRequired ?? false,
          isPrimaryKey: item.isPrimaryKey ?? isId,
          macroValue: item.macroValue || (isId ? '${#ID.SNOWFLAKE}' : undefined),
          options: item.options || [],
        };
      });
    };

    if (dataSource.length === 0) {
      const prepared = prepareFields(newFields);
      setDataSource(prepared);
      setEditableKeys(prepared.map((r) => r.id));
      return;
    }

    Modal.confirm({
      title: '导入合并选项',
      content: '当前列表中已有字段配置，是将其追加到末尾还是清空并覆盖现有字段？',
      okText: '追加',
      cancelText: '覆盖',
      onOk: () => {
        const prepared = prepareFields(newFields);
        setDataSource((prev) => [...prev, ...prepared]);
        setEditableKeys((prev) => [...prev, ...prepared.map((r) => r.id)]);
      },
      onCancel: () => {
        const prepared = prepareFields(newFields);
        setDataSource(prepared);
        setEditableKeys(prepared.map((r) => r.id));
      },
    });
  };

  // ---- 拖拽传感器 ----
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  // ---- 加载详情 ----
  useEffect(() => {
    if (open && modelId) {
      loadData();
    } else {
      setDataSource([]);
      setEditableKeys([]);
      setModelBaseInfo(null);
    }
  }, [open, modelId]);

  const loadData = async () => {
    try {
      setLoading(true);
      const res = await getModelDetail(modelId);
      const data = res?.data || res;
      setModelBaseInfo(data);
      if (data?.fieldsSchema) {
        try {
          // 判断是字符串 JSON 还是已解析对象
          const parsed = typeof data.fieldsSchema === 'string'
            ? JSON.parse(data.fieldsSchema)
            : data.fieldsSchema;

          if (Array.isArray(parsed)) {
            // 给已有数据补充唯一 ID 保证 Editable 行状态稳定
            const rows = parsed.map(item => ({ ...item, id: item.id || `field_${Date.now()}_${Math.random().toString(36).substring(2, 9)}` }));
            setDataSource(rows);
            // 默认所有数据都不是编辑状态，或者我们希望一直可编辑？
            // 如果需要保存，可以直接开启全部的编辑状态
            setEditableKeys(rows.map(r => r.id));
          } else {
            setDataSource([]);
          }
        } catch (e) {
          console.error("解析 fieldsSchema 失败", e);
          setDataSource([]);
        }
      } else {
        setDataSource([]);
      }
    } catch {
      message.error('加载模型详情失败');
    } finally {
      setLoading(false);
    }
  };

  // ---- 保存更改 ----
  const handleSave = async () => {
    // 强制关闭所有还在激活状态的编辑行，使其数据刷入 dataSource
    // EditableProTable 会自动同步 dataSource
    try {
      if (!modelBaseInfo) return;
      const payload = {
        ...modelBaseInfo,
        // 这里把 dataSource 转回 JSON 字符串给后端
        fieldsSchema: JSON.stringify(dataSource),
      };

      setLoading(true);
      await updateModel(modelId, payload);
      message.success('字段配置保存成功');
      onRefresh?.();
      onOpenChange(false);
    } catch {
      message.error('保存失败');
    } finally {
      setLoading(false);
    }
  };

  // ---- 拖拽排序逻辑 ----
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      setDataSource((prev) => {
        const oldIndex = prev.findIndex((item) => item.id === active.id);
        const newIndex = prev.findIndex((item) => item.id === over.id);
        return arrayMove(prev, oldIndex, newIndex);
      });
    }
  };

  // ---- 列定义 -----------------------------------------------------------------
  const columns: ProColumns<FieldSchema>[] = [
    {
      title: '排序',
      dataIndex: 'sort',
      width: 50,
      align: 'center',
      editable: false,
      render: () => <DragHandle />,
    },
    {
      title: '字段英文名',
      dataIndex: 'fieldId',
      width: 140,
      formItemProps: { rules: [{ required: true, message: '必填' }] },
      fieldProps: {
        size: 'small',
        placeholder: '如: user_name',
      },
    },
    {
      title: '字段中文名',
      dataIndex: 'fieldName',
      width: 120,
      formItemProps: { rules: [{ required: true, message: '必填' }] },
      fieldProps: {
        size: 'small',
        placeholder: '如: 用户名称',
      },
    },
    {
      title: '数据库类型',
      dataIndex: 'dbType',
      valueType: 'select',
      width: 120,
      fieldProps: { size: 'small' },
      valueEnum: {
        VARCHAR: { text: 'VARCHAR', status: 'Default' },
        INT: { text: 'INT', status: 'Default' },
        DATETIME: { text: 'DATETIME', status: 'Default' },
        BOOLEAN: { text: 'BOOLEAN', status: 'Default' },
        TEXT: { text: 'TEXT', status: 'Default' },
        ENUM: { text: 'ENUM', status: 'Default' },
      },
    },
    {
      title: 'UI 组件类型',
      dataIndex: 'uiType',
      valueType: 'select',
      width: 130,
      fieldProps: { size: 'small' },
      valueEnum: {
        'input-text': { text: '单行文本', status: 'Default' },
        'input-number': { text: '数字输入', status: 'Default' },
        'textarea': { text: '多行文本', status: 'Default' },
        'select': { text: '下拉框', status: 'Default' },
        'radio': { text: '单选', status: 'Default' },
        'input-date': { text: '日期选择', status: 'Default' },
        'switch': { text: '开关', status: 'Default' },
      },
    },
    {
      title: '主键',
      dataIndex: 'isPrimaryKey',
      valueType: 'switch',
      width: 80,
      // 注意：不能用函数形式的 fieldProps，会被 ProTable 序列化导致循环引用警告
      // 排他逻辑统一在 editable.onValuesChange 中处理
      fieldProps: { size: 'small' },
    },
    {
      title: '必填',
      dataIndex: 'isRequired',
      valueType: 'switch',
      fieldProps: { size: 'small' },
      width: 80,
    },
    {
      title: '列表展示',
      dataIndex: 'showInList',
      valueType: 'switch',
      fieldProps: { size: 'small' },
      width: 100,
    },
    {
      title: '可用作查询',
      dataIndex: 'isSearchable',
      valueType: 'switch',
      fieldProps: { size: 'small' },
      width: 100,
    },
    {
      title: '长度',
      dataIndex: 'maxLength',
      width: 80,
      dependencies: ['dbType', 'uiType'],
      renderFormItem: (_, config, form) => {
        const rowKey = config.recordKey as string;
        if (!rowKey) return null;
        const dbType = form.getFieldValue([rowKey, 'dbType']);
        const uiType = form.getFieldValue([rowKey, 'uiType']);
        const disabled = dbType !== 'VARCHAR' && !['input-text', 'textarea'].includes(uiType);
        return (
          <InputNumber
            size="small"
            min={1}
            max={65535}
            disabled={disabled}
            placeholder={disabled ? '-' : '如 255'}
            style={{ width: '100%' }}
            value={form.getFieldValue([rowKey, 'maxLength'])}
            onChange={(val) => {
              form.setFieldValue([rowKey, 'maxLength'], val);
              setDataSource(prev => prev.map(item =>
                item.id === rowKey ? { ...item, maxLength: val ?? undefined } : item
              ));
            }}
          />
        );
      },
    },
    {
      title: '校验规则',
      dataIndex: 'validations',
      width: 200,
      dependencies: ['validations', 'customRegexPattern'],
      renderFormItem: (_, config, form) => {
        const rowKey = config.recordKey as string;
        if (!rowKey) return null;
        const currentVals: string[] = form.getFieldValue([rowKey, 'validations']) ?? config.record?.validations ?? [];
        const hasCustomRegex = currentVals.includes('customRegex');
        const regexVal = form.getFieldValue([rowKey, 'customRegexPattern']) ?? config.record?.customRegexPattern ?? '';

        return (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Select
              size="small"
              placeholder="请选择"
              value={currentVals}
              style={{ width: '100%' }}
              onChange={(val: string[]) => {
                form.setFieldValue([rowKey, 'validations'], val);
                setDataSource(prev => prev.map(item =>
                  item.id === rowKey ? { ...item, validations: val } : item
                ));
              }}
              options={[
                { label: '邮箱格式', value: 'isEmail' },
                { label: '手机号', value: 'isPhoneNumber' },
                { label: '网址格式', value: 'isUrl' },
                { label: '纯数字', value: 'isNumeric' },
                { label: '纯字母', value: 'isAlpha' },
                { label: '自定义正则', value: 'customRegex' },
              ]}
            />
            {hasCustomRegex && (
              <Input
                size="small"
                placeholder="输入正则表达式..."
                defaultValue={regexVal}
                onBlur={(e) => {
                  const v = e.target.value;
                  form.setFieldValue([rowKey, 'customRegexPattern'], v);
                  setDataSource(prev => prev.map(item =>
                    item.id === rowKey ? { ...item, customRegexPattern: v } : item
                  ));
                }}
              />
            )}
          </Space>
        );
      },
    },
    {
      title: '默认值',
      dataIndex: 'dataConfig',
      width: 160,
      dependencies: ['uiType', 'fieldId'],
      renderFormItem: (schema, config, form) => {
        const rowKey = config.recordKey as string;
        if (!rowKey) return null;
        const uiType = form.getFieldValue([rowKey, 'uiType']);
        const fieldId = form.getFieldValue([rowKey, 'fieldId']);
        let macroValue = form.getFieldValue([rowKey, 'macroValue']);

        if (fieldId === 'id' && !macroValue) {
          setTimeout(() => {
            form.setFieldValue([rowKey, 'macroValue'], '${#ID.SNOWFLAKE}');
            setDataSource(prev => prev.map(item => item.id === rowKey ? { ...item, macroValue: '${#ID.SNOWFLAKE}' } : item));
          }, 0);
          macroValue = '${#ID.SNOWFLAKE}';
        }

        if (uiType === 'select' || uiType === 'radio') {
          const options = form.getFieldValue([rowKey, 'options']) ?? config.record?.options ?? [];
          return (
            <Button
              size="small"
              onClick={() => {
                const mappedOptions = options.map((o: any) => ({ ...o, id: o.id || `opt_${Math.random().toString(36).substring(2)}` }));
                setOptModalData({ rowKey, form, options: mappedOptions });
                setOptEditableKeys(mappedOptions.map((o: any) => o.id));
              }}
            >
              ⚙️ 选项 ({options.length})
            </Button>
          );
        }

        return (
          <AutoComplete
            size="small"
            options={[
              { value: '${#ID.SNOWFLAKE}' },
              { value: '${#ID.UUID}' },
              { value: '${#TIME.NOW}' },
              { value: '${#USER.ID}' },
            ]}
            value={macroValue}
            onChange={(val) => {
              form.setFieldValue([rowKey, 'macroValue'], val);
              setDataSource(prev => prev.map(item => item.id === rowKey ? { ...item, macroValue: val } : item));
            }}
            placeholder="默认值或宏..."
            allowClear
            style={{ width: '100%' }}
          />
        );
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (text, record, _, action) => [
        <a key="delete" onClick={() => setDataSource(dataSource.filter((item) => item.id !== record.id))} style={{ color: '#ff4d4f' }}>
          移除
        </a>,
      ],
    },
  ];

  // ---- 工具栏快捷导入菜单 ----
  const importMenuProps: MenuProps = {
    items: [
      {
        key: 'db',
        label: '从数据库表导入',
        onClick: () => setDbModalVisible(true),
      },
      {
        key: 'ddl',
        label: '解析 DDL 导入',
        onClick: () => setDdlModalVisible(true),
      },
    ],
  };

  return (
    <Drawer
      title="配置模型字段"
      width="80vw"
      open={open}
      onClose={() => onOpenChange(false)}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={() => onOpenChange(false)}>取消</Button>
          <Button type="primary" onClick={handleSave} loading={loading}>
            保存配置
          </Button>
        </Space>
      }
    >
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={dataSource.map((item) => item.id)} strategy={verticalListSortingStrategy}>
          <EditableProTable<FieldSchema>
            size="small"
            editableFormRef={editableFormRef}
            rowKey="id"
            headerTitle="模型字段列表"
            loading={loading}
            toolBarRender={() => [
              <Dropdown menu={importMenuProps} key="import-dropdown">
                <Button>
                  快捷导入 <DownOutlined />
                </Button>
              </Dropdown>,
            ]}
            columns={columns}
            value={dataSource as FieldSchema[]}
            onChange={(val) => setDataSource([...val])}
            recordCreatorProps={{
              position: 'bottom',
              newRecordType: 'dataSource',
              record: () => ({
                id: `field_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
                fieldId: '',
                fieldName: '',
                dbType: 'VARCHAR',
                uiType: 'input-text',
                isPrimaryKey: false,
                isRequired: false,
                showInList: true,
                isSearchable: false,
                validations: [],
                customRegexPattern: '',
                maxLength: undefined,
                macroValue: '',
                options: [],
              }),
            }}
            editable={{
              type: 'multiple',
              editableKeys,
              onChange: setEditableKeys,
              onValuesChange: (record, recordList) => {
                // 过滤掉新行尚未初始化时可能出现的 undefined 条目
                const safeList = (recordList as any[]).filter(Boolean) as FieldSchema[];
                let updatedList = [...safeList];

                // 单主键排他：某行 isPrimaryKey 切换为 true 时
                if (record?.isPrimaryKey === true) {
                  updatedList = updatedList.map(item => ({
                    ...item,
                    isPrimaryKey: item?.id === record.id,
                  }));
                  // 同步其他行的 form 内部值，让 Switch UI 真实翻转
                  updatedList.forEach(item => {
                    if (item?.id && item.id !== record.id) {
                      editableFormRef.current?.setRowData?.(item.id, { isPrimaryKey: false });
                    }
                  });
                } else if (record?.isPrimaryKey === false) {
                  updatedList = updatedList.map(item =>
                    item?.id === record.id ? { ...item, isPrimaryKey: false } : item
                  );
                }

                setDataSource(updatedList);
              },
              actionRender: (row, config, dom) => [dom.delete],
            }}
            components={{
              body: {
                row: SortableRow,
              },
            }}
          />
        </SortableContext>
      </DndContext>

      {/* ========== 解析 DDL 导入弹窗 ========== */}
      <ModalForm
        title="解析 DDL 导入"
        width={600}
        open={ddlModalVisible}
        onOpenChange={setDdlModalVisible}
        modalProps={{ destroyOnClose: true }}
        onFinish={async (values) => {
          const hide = message.loading('正在解析...', 0);
          try {
            const res = await request('/flow-api/models/import/from-ddl', {
              method: 'POST',
              data: { ddl: values.ddl, dbType: values.dbType },
            });
            hide();

            // 兼容 Umi Request 可能自动解包 response.data 为真实数组的情形
            const actualData = res?.data || res;
            if (actualData && Array.isArray(actualData)) {
              handleMergeFields(actualData);
              setDdlModalVisible(false);
              return true;
            }
          } catch (e: any) {
            hide();
            message.error('导入失败：' + (e?.message || '未知错误'));
          }
          return false;
        }}
      >
        <ProFormSelect
          name="dbType"
          label="数据库类型"
          options={[
            { label: 'MySQL', value: 'mysql' },
            { label: 'PostgreSQL', value: 'postgresql' },
          ]}
          placeholder="默认自动识别，可选填"
        />
        <ProFormTextArea
          name="ddl"
          label="DDL 语句"
          rules={[{ required: true, message: '必须输入 DDL 语句' }]}
          fieldProps={{ rows: 8, placeholder: '在此粘贴 CREATE TABLE 建表语句' }}
        />
      </ModalForm>

      {/* ========== 从数据库表导入弹窗 ========== */}
      <ModalForm
        title="从数据库表导入"
        width={400}
        open={dbModalVisible}
        onOpenChange={setDbModalVisible}
        modalProps={{ destroyOnClose: true }}
        initialValues={{
          datasourceCode: modelBaseInfo?.datasource,
          tableName: modelBaseInfo?.tableName,
        }}
        onFinish={async (values) => {
          const hide = message.loading('正在导入...', 0);
          try {
            const datasourceCode = values.datasourceCode?.trim();
            const tableName = values.tableName?.trim();
            const params: any = { tableName };

            // 避免发送包含 Tomcat 敏感字符的 [DEFAULT]，后端会自行兜底补全
            if (datasourceCode && datasourceCode !== '[DEFAULT]') {
              params.datasourceCode = datasourceCode;
            }

            const res = await request('/flow-api/models/import/from-db', {
              method: 'GET',
              params,
            });
            hide();

            // 兼容 Umi Request 可能自动解包 response.data 为真实数组的情形
            const actualData = res?.data || res;
            if (actualData && Array.isArray(actualData)) {
              handleMergeFields(actualData);
              setDbModalVisible(false);
              return true;
            }
          } catch (e: any) {
            hide();
            message.error('导入失败：' + (e?.message || '未知错误'));
          }
          return false;
        }}
      >
        <ProFormSelect
          name="datasourceCode"
          label="目标数据源"
          rules={[{ required: true, message: '请选择数据源' }]}
          request={async () => {
            try {
              const res = await queryDataSourceList();
              return res.map((item: any) => ({ label: item.name, value: item.code }));
            } catch {
              return [];
            }
          }}
          placeholder="请选择目标数据源"
        />
        <ProFormText
          name="tableName"
          label="物理表名"
          rules={[{ required: true, message: '物理表名必填' }]}
          placeholder="如：t_user"
        />
      </ModalForm>

      {/* ========== 配置选项弹窗 ========== */}
      <Modal
        title="配置选项 (关联字典)"
        open={!!optModalData}
        width={600}
        onCancel={() => setOptModalData(null)}
        onOk={() => {
          if (optModalData) {
            optModalData.form.setFieldValue([optModalData.rowKey, 'options'], optModalData.options);
            setDataSource(prev => prev.map(item => item.id === optModalData.rowKey ? { ...item, options: optModalData.options } : item));
          }
          setOptModalData(null);
        }}
        destroyOnClose
      >
        <EditableProTable
          rowKey="id"
          headerTitle={false}
          recordCreatorProps={{
            position: 'bottom',
            newRecordType: 'dataSource',
            record: () => ({ id: `opt_${Date.now()}_${Math.random().toString(36).substring(2)}`, label: '', value: '' })
          }}
          columns={[
            {
              title: '显示名 (label)',
              dataIndex: 'label',
              formItemProps: { rules: [{ required: true, message: '必填' }] }
            },
            {
              title: '存储值 (value)',
              dataIndex: 'value',
              formItemProps: { rules: [{ required: true, message: '必填' }] }
            },
            {
              title: '操作',
              valueType: 'option',
              width: 100,
              render: (_, record) => (
                <a
                  onClick={() => {
                    if (optModalData) {
                      setOptModalData({
                        ...optModalData,
                        options: optModalData.options.filter(o => o.id !== record.id)
                      });
                    }
                  }}
                  style={{ color: '#ff4d4f' }}
                >
                  移除
                </a>
              )
            }
          ]}
          value={optModalData?.options || []}
          onChange={(val) => {
            if (optModalData) {
              setOptModalData({ ...optModalData, options: [...val] });
            }
          }}
          editable={{
            type: 'multiple',
            editableKeys: optEditableKeys,
            onChange: setOptEditableKeys,
            onValuesChange: (record, recordList) => {
              if (optModalData) {
                setOptModalData({ ...optModalData, options: recordList });
              }
            },
            actionRender: (row, config, dom) => [dom.delete],
          }}
        />
      </Modal>
    </Drawer>
  );
};

export default FieldEditorDrawer;

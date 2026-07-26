/**
 * ApiDataViewColumnsTab
 * ─────────────────────────────────────────────────────────────────────────────
 * ApiDataViewDrawer「列配置」Tab：导出开关、列编辑与同步。
 */
import React from 'react';
import { Button, Form, Input, InputNumber, Space, Switch, Table } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { syncColumnsFromContract } from './apiDataViewUtils';
import type { ViewExportColumn, FlowController } from '@/services/flow/flowController';

export interface ApiDataViewColumnsTabProps {
  cfgForm: FormInstance;
  columns: ViewExportColumn[];
  setColumns: React.Dispatch<React.SetStateAction<ViewExportColumn[]>>;
  markDirty: () => void;
  saving: boolean;
  saveColumnConfig: (override?: any, quiet?: boolean) => void;
  detail: FlowController | null;
}

const ApiDataViewColumnsTab: React.FC<ApiDataViewColumnsTabProps> = ({
  cfgForm,
  columns,
  setColumns,
  markDirty,
  saving,
  saveColumnConfig,
  detail,
}) => {
  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Form
        form={cfgForm}
        layout="inline"
        onValuesChange={() => markDirty()}
      >
        <Form.Item name="enabled" label="启用导出" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="sheetName" label="Sheet 名">
          <Input style={{ width: 120 }} />
        </Form.Item>
        <Form.Item name="maxExportRows" label="最大导出行数">
          <InputNumber min={1} max={50000} style={{ width: 120 }} />
        </Form.Item>
      </Form>
      <Space>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => {
            setColumns(syncColumnsFromContract(detail?.contract, columns));
            markDirty();
          }}
        >
          从响应契约同步列
        </Button>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={saving}
          onClick={() => saveColumnConfig()}
        >
          保存列配置到草稿
        </Button>
      </Space>
      <Table
        size="small"
        rowKey="field"
        pagination={false}
        dataSource={columns}
        columns={[
          { title: '字段', dataIndex: 'field', width: 160 },
          {
            title: '中文表头',
            dataIndex: 'header',
            render: (v, row, idx) => (
              <Input
                value={v}
                onChange={(e) => {
                  const next = [...columns];
                  next[idx] = { ...row, header: e.target.value };
                  setColumns(next);
                  markDirty();
                }}
              />
            ),
          },
          {
            title: '显示',
            dataIndex: 'visible',
            width: 70,
            render: (v, row, idx) => (
              <Switch
                checked={v !== false}
                onChange={(checked) => {
                  const next = [...columns];
                  next[idx] = { ...row, visible: checked };
                  setColumns(next);
                  markDirty();
                }}
              />
            ),
          },
          {
            title: '导出',
            dataIndex: 'exportable',
            width: 70,
            render: (v, row, idx) => (
              <Switch
                checked={v !== false}
                onChange={(checked) => {
                  const next = [...columns];
                  next[idx] = { ...row, exportable: checked };
                  setColumns(next);
                  markDirty();
                }}
              />
            ),
          },
        ]}
      />
    </Space>
  );
};

export default ApiDataViewColumnsTab;

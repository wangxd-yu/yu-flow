/**
 * ReqSchemaPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「API 文档定义 · 请求」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理 Query Params / Body / Headers 三类请求参数的 Schema 定义
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React from 'react';
import { Alert, Button, Empty, Input, Radio, Space, Tabs } from 'antd';
import { InfoCircleOutlined, EyeOutlined, ImportOutlined } from '@ant-design/icons';
import SchemaTreeTable from '../ApiContractDesigner/SchemaTreeTable';
import type { SchemaNode, BodyType } from '../ApiContractDesigner/types';

// ═══════════════════════════════════════════════════════════════════════════
//  常量
// ═══════════════════════════════════════════════════════════════════════════

const BODY_TYPE_OPTIONS: { label: string; value: BodyType }[] = [
  { label: 'none', value: 'none' },
  { label: 'form-data', value: 'form-data' },
  { label: 'x-www-form-urlencoded', value: 'x-www-form-urlencoded' },
  { label: 'json', value: 'json' },
  { label: 'xml', value: 'xml' },
  { label: 'raw', value: 'raw' },
];

// ═══════════════════════════════════════════════════════════════════════════
//  Props
// ═══════════════════════════════════════════════════════════════════════════

export interface ReqSchemaPanelProps {
  queryParams: SchemaNode[];
  onQueryParamsChange: (v: SchemaNode[]) => void;
  headers: SchemaNode[];
  onHeadersChange: (v: SchemaNode[]) => void;
  bodyNodes: SchemaNode[];
  onBodyNodesChange: (v: SchemaNode[]) => void;
  bodyType: BodyType;
  onBodyTypeChange: (v: BodyType) => void;
  rawBody: string;
  onRawBodyChange: (v: string) => void;
}

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const ReqSchemaPanel: React.FC<ReqSchemaPanelProps> = ({
  queryParams, onQueryParamsChange,
  headers, onHeadersChange,
  bodyNodes, onBodyNodesChange,
  bodyType, onBodyTypeChange,
  rawBody, onRawBodyChange,
}) => {
  /** Body 内容渲染 — 根据 bodyType 切换不同编辑器 */
  const renderBodyContent = () => {
    switch (bodyType) {
      case 'none':
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该请求没有 Body" style={{ padding: '40px 0' }} />;
      case 'json':
        return <SchemaTreeTable value={bodyNodes} onChange={onBodyNodesChange} hideToolbar />;
      case 'form-data':
      case 'x-www-form-urlencoded':
        return <SchemaTreeTable flat value={bodyNodes} onChange={onBodyNodesChange} />;
      case 'raw':
      case 'xml':
        return (
          <Input.TextArea
            value={rawBody}
            onChange={(e) => onRawBodyChange(e.target.value)}
            placeholder={bodyType === 'xml' ? '请输入 XML 内容...' : '请输入原始内容...'}
            autoSize={{ minRows: 8, maxRows: 20 }}
            style={{ fontFamily: 'monospace', fontSize: 13 }}
          />
        );
      default:
        return null;
    }
  };

  return (
    <div>
      <Alert
        message="文档定义说明"
        description="此处参数仅用于生成 Swagger/API 文档及 Mock 数据，底层真实运行逻辑请在「服务实现」节点中配置。"
        type="info"
        showIcon
        icon={<InfoCircleOutlined />}
        closable
        style={{ marginBottom: 16 }}
      />

      <Tabs
        defaultActiveKey="params"
        size="small"
        items={[
          {
            key: 'params',
            label: 'Params',
            children: <SchemaTreeTable flat value={queryParams} onChange={onQueryParamsChange} />,
          },
          {
            key: 'body',
            label: 'Body',
            children: (
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                  <Radio.Group
                    value={bodyType}
                    onChange={(e) => onBodyTypeChange(e.target.value)}
                    optionType="button"
                    buttonStyle="solid"
                    size="small"
                  >
                    {BODY_TYPE_OPTIONS.map((opt) => (
                      <Radio.Button key={opt.value} value={opt.value}>{opt.label}</Radio.Button>
                    ))}
                  </Radio.Group>
                  {bodyType === 'json' && (
                    <Space size={4}>
                      <Button size="small" type="text" icon={<EyeOutlined />}>预览 Schema</Button>
                      <Button size="small" type="text" icon={<ImportOutlined />}>导入 Schema</Button>
                    </Space>
                  )}
                </div>
                {renderBodyContent()}
              </div>
            ),
          },
          {
            key: 'headers',
            label: 'Headers',
            children: <SchemaTreeTable flat value={headers} onChange={onHeadersChange} />,
          },
        ]}
      />
    </div>
  );
};

export default React.memo(ReqSchemaPanel);

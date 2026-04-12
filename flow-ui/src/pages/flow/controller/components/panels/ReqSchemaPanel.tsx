/**
 * ReqSchemaPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「API 文档定义 · 请求」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理 Query Params / Body / Headers 三类请求参数的 Schema 定义
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useMemo } from 'react';
import { Alert, Empty, Input, Radio, Space, Tabs } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import SchemaTreeTable from '../ApiContractDesigner/SchemaTreeTable';
import useSchemaDrawer from '../ApiContractDesigner/useSchemaDrawer';
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
  method?: string;
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

const ROOT_ID = 'root';

const ReqSchemaPanel: React.FC<ReqSchemaPanelProps> = ({
  method = '',
  queryParams, onQueryParamsChange,
  headers, onHeadersChange,
  bodyNodes, onBodyNodesChange,
  bodyType, onBodyTypeChange,
  rawBody, onRawBodyChange,
}) => {
  // ── 为 Body JSON 模式的 SchemaNode[] 构建带根节点的数据
  //    与 SchemaTreeTable 内部 dataSource 逻辑保持一致 ──────────
  const bodyDataSource = useMemo<SchemaNode[]>(() => {
    if (bodyNodes.length === 1 && bodyNodes[0].id === ROOT_ID) return bodyNodes;
    return [{
      id: ROOT_ID,
      name: '根节点',
      type: 'object' as const,
      required: false,
      description: '',
      children: bodyNodes.length > 0 ? bodyNodes : [],
    }];
  }, [bodyNodes]);

  // ── 复用 Schema 预览 / 导入 Hook ──────────────────────────
  const { previewBtn, importBtn, drawers: schemaDrawers } = useSchemaDrawer({
    nodes: bodyDataSource,
    onNodesChange: onBodyNodesChange,
  });

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
        message="网关前置定义说明"
        description="此处定义的参数不仅用于生成 API 文档及 Mock 数据，还将作为 API 网关的前置校验规则。不符合规则的请求将被网关直接拦截屏蔽，不会进入「服务实现」的底层画布中。"
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
          ...(['GET', 'DELETE'].includes(method.toUpperCase())
            ? []
            : [
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
                          {previewBtn}
                          {importBtn}
                        </Space>
                      )}
                    </div>
                    {renderBodyContent()}
                  </div>
                ),
              },
            ]),
          {
            key: 'headers',
            label: 'Headers',
            children: <SchemaTreeTable flat value={headers} onChange={onHeadersChange} />,
          },
        ]}
      />

      {/* Schema 预览 / 导入 Drawers */}
      {schemaDrawers}
    </div>
  );
};

export default React.memo(ReqSchemaPanel);

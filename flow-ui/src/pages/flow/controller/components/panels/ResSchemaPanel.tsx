/**
 * ResSchemaPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「API 文档定义 · 响应」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理响应体 Schema 定义，含状态码和描述
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React from 'react';
import { Alert, Button, Input, InputNumber, Space } from 'antd';
import { InfoCircleOutlined, ImportOutlined } from '@ant-design/icons';
import SchemaTreeTable from '../ApiContractDesigner/SchemaTreeTable';
import type { SchemaNode } from '../ApiContractDesigner/types';

// ═══════════════════════════════════════════════════════════════════════════
//  Props
// ═══════════════════════════════════════════════════════════════════════════

export interface ResSchemaPanelProps {
  responseBody: SchemaNode[];
  onResponseBodyChange: (v: SchemaNode[]) => void;
  responseDesc: string;
  onResponseDescChange: (v: string) => void;
  statusCode: number;
  onStatusCodeChange: (v: number) => void;
}

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const ResSchemaPanel: React.FC<ResSchemaPanelProps> = ({
  responseBody, onResponseBodyChange,
  responseDesc, onResponseDescChange,
  statusCode, onStatusCodeChange,
}) => {
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

      {/* 操作栏 */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
        <Button type="dashed" icon={<ImportOutlined />}>
          从 JSON 自动推导
        </Button>
      </div>

      {/* 状态码 + 描述 */}
      <Space style={{ marginBottom: 12 }}>
        <span style={{ fontWeight: 500 }}>状态码：</span>
        <InputNumber
          value={statusCode}
          onChange={(v) => onStatusCodeChange(v ?? 200)}
          min={100}
          max={599}
          style={{ width: 100 }}
        />
        <Input
          value={responseDesc}
          onChange={(e) => onResponseDescChange(e.target.value)}
          placeholder="响应描述"
          style={{ width: 240 }}
          addonBefore="描述"
        />
      </Space>

      {/* 响应参数表格 */}
      <SchemaTreeTable value={responseBody} onChange={onResponseBodyChange} />
    </div>
  );
};

export default React.memo(ResSchemaPanel);

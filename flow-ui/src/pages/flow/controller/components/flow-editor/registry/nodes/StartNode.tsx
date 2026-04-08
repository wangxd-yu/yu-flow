import React from 'react';
import { Input, Button, Divider, Select, Space, Typography } from 'antd';
import { Graph, Node, NodeMetadata } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import type { NodeData, HttpMethod, StartRequestConfig, VarDef } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';

const { Text } = Typography;

const ICONS = {
  request: (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
      <path d="M140 160h744v72H140zM140 376h744v72H140zM140 592h744v72H140zM140 808h744v72H140z" />
    </svg>
  ),
  chevron: (
    <svg width="10" height="10" viewBox="0 0 1024 1024" fill="currentColor">
      <path d="M765.7 486.8L314.9 134.7A7.97 7.97 0 00302 141v77.3c0 4.9 2.3 9.6 6.1 12.6l360 281.1-360 281.1c-3.9 3-6.1 7.7-6.1 12.6V883c0 6.7 7.7 10.4 12.9 6.3l450.8-352.1a31.96 31.96 0 000-50.4z" />
    </svg>
  ),
};

// =============================================================================
// 1. React Component for Node Visualization (Middle Canvas)
// =============================================================================

export const StartNode = ({ node }: { node: Node }) => {
  const [data, setData] = React.useState<NodeData>(node.getData());
  const [attrs, setAttrs] = React.useState(node.getAttrs());

  React.useEffect(() => {
    const onDataChange = () => {
      setData({ ...node.getData() } as NodeData);
    };
    const onAttrsChange = () => {
      setAttrs({ ...node.getAttrs() });
    };
    node.on('change:data', onDataChange);
    node.on('change:attrs', onAttrsChange);
    return () => {
      node.off('change:data', onDataChange);
      node.off('change:attrs', onAttrsChange);
    };
  }, [node]);

  // Use generic type assertion or specific type if available
  const uiData = (data as any);
  const start = uiData?.start;
  const method = start?.method || 'GET';
  const name = uiData?.name || 'Request';

  const headersCount = Array.isArray(start?.headers) ? start.headers.length : 0;
  const paramsCount = Array.isArray(start?.params) ? start.params.length : 0;
  const bodyCount = Array.isArray(start?.body) ? start.body.length : 0;
  const showBody = method === 'POST' || method === 'PUT';

  // Dynamic height calculation
  // Header: 44px
  // Padding top: 12px
  // Content items: 20px each
  // Padding bottom: 12px
  const contentItems = showBody ? 3 : 2;
  const height = 44 + 12 + (contentItems * 20) + 12;

  // Sync node size when content changes
  React.useEffect(() => {
    const size = node.getSize();
    if (size.height !== height) {
      node.resize(260, height);
    }
  }, [height, node]);

  // Extract styles from attrs
  // FlowEditor sets body/stroke, body/strokeWidth, body/fill
  const borderColor = attrs?.body?.stroke || '#d9d9d9';
  const borderWidth = attrs?.body?.strokeWidth || 1;
  const backgroundColor = attrs?.body?.fill || '#ffffff';

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: backgroundColor as string,
        border: `${borderWidth}px solid ${borderColor}`,
        borderRadius: 10,
        display: 'flex',
        overflow: 'hidden',
        position: 'relative',
        boxSizing: 'border-box',
        pointerEvents: 'none',
      }}
    >
      {/* Left Bar */}
      <div
        style={{
          width: 12,
          height: '100%',
          background: '#ff7a45',
          flexShrink: 0,
          pointerEvents: 'auto',
        }}
      />

      {/* Content */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', pointerEvents: 'auto' }}>
        {/* Header */}
        <div
          style={{
            height: 44,
            display: 'flex',
            alignItems: 'center',
            padding: '0 12px',
            borderBottom: '1px solid #f0f0f0',
            position: 'relative',
          }}
        >
          {/* Icon */}
          <div style={{ marginRight: 8, display: 'flex', alignItems: 'center', color: '#8c8c8c' }}>
            {ICONS.request}
          </div>

          {/* Title */}
          <Text strong style={{ fontSize: 12, marginRight: 8, flex: 1 }} ellipsis>
            {name}
          </Text>

          {/* Method */}
          <Text type="secondary" style={{ fontSize: 10, marginRight: 8 }}>
            {method}
          </Text>

          {/* Trigger Text */}
          <Text type="secondary" style={{ fontSize: 12 }}>
            Change trigger
          </Text>

          {/* Chevron */}
          <div style={{ marginLeft: 4, color: '#8c8c8c', transform: 'rotate(-90deg)' }}>
            {ICONS.chevron}
          </div>
        </div>

        {/* Body Content */}
        <div style={{ padding: '12px 12px 12px 0', display: 'flex', flexDirection: 'column', gap: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'flex-end', height: 20, alignItems: 'center' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Headers ({headersCount})
            </Text>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', height: 20, alignItems: 'center' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Params ({paramsCount})
            </Text>
          </div>
          {showBody && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', height: 20, alignItems: 'center' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Body ({bodyCount})
              </Text>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// 2. React Component for Properties Panel (Right Panel)
// =============================================================================

function InputProperties(props: { data: NodeData; onChange: (next: NodeData) => void }) {
  const { data, onChange } = props;

  const raw: any = data as any;
  const start: StartRequestConfig = raw.start ?? { mode: 'request', method: 'GET', headers: [], params: [], body: [] };
  const name: string = raw.name ?? 'Request';

  const apply = React.useCallback(
    (patch: Partial<StartRequestConfig> & { name?: string }) => {
      const next: any = { ...raw };
      next.name = patch.name ?? name;
      next.start = {
        ...start,
        ...patch,
        headers: patch.headers ?? start.headers ?? [],
        params: patch.params ?? start.params ?? [],
        body: patch.body ?? start.body ?? [],
      } satisfies StartRequestConfig;
      onChange(next);
    },
    [name, onChange, raw, start],
  );

  const updateVar = React.useCallback(
    (group: 'headers' | 'params' | 'body', index: number, patch: Partial<VarDef>) => {
      const list = Array.isArray((start as any)[group]) ? ([...(start as any)[group]] as VarDef[]) : [];
      const prev = list[index] ?? ({ key: createId('var'), name: '', type: 'string' } satisfies VarDef);
      list[index] = { ...prev, ...patch } as VarDef;
      apply({ [group]: list } as any);
    },
    [apply, start],
  );

  const addVar = React.useCallback(
    (group: 'headers' | 'params' | 'body') => {
      const list = Array.isArray((start as any)[group]) ? ([...(start as any)[group]] as VarDef[]) : [];
      list.push({ key: createId('var'), name: '', type: 'string' } satisfies VarDef);
      apply({ [group]: list } as any);
    },
    [apply, start],
  );

  const removeVar = React.useCallback(
    (group: 'headers' | 'params' | 'body', index: number) => {
      const list = Array.isArray((start as any)[group]) ? ([...(start as any)[group]] as VarDef[]) : [];
      list.splice(index, 1);
      apply({ [group]: list } as any);
    },
    [apply, start],
  );

  return (
    <div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div>
          <div style={{ marginBottom: 6 }}>
            <Text strong>名称</Text>
          </div>
          <Input value={name} placeholder="例如：Request" onChange={(e) => apply({ name: e.target.value })} />
        </div>

        <div>
          <div style={{ marginBottom: 6 }}>
            <Text strong>触发器</Text>
          </div>
          <Select
            value={start.mode}
            style={{ width: '100%' }}
            options={[
              { value: 'request', label: 'Request' },
              { value: 'method', label: 'Method（预留）', disabled: true },
              { value: 'schedule', label: 'Schedule（预留）', disabled: true },
            ]}
            onChange={(v) => apply({ mode: v as any })}
          />
        </div>

        <div>
          <div style={{ marginBottom: 6 }}>
            <Text strong>Method</Text>
          </div>
          <Select
            value={start.method}
            style={{ width: '100%' }}
            options={[
              { value: 'GET', label: 'GET' },
              { value: 'POST', label: 'POST' },
              { value: 'PUT', label: 'PUT' },
              { value: 'DELETE', label: 'DELETE' },
            ]}
            onChange={(v) => apply({ method: v as HttpMethod })}
          />
        </div>
      </div>

      <Divider style={{ margin: '12px 0' }} />

      <Text strong>Headers</Text>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
        {(start.headers || []).map((h, idx) => (
          <Space key={h.key || idx} align="baseline">
            <Input
              placeholder="header 名称"
              style={{ width: 140 }}
              value={h.name}
              onChange={(e) => updateVar('headers', idx, { name: e.target.value })}
            />
            <Select
              style={{ width: 110 }}
              value={h.type}
              options={[
                { value: 'string', label: 'string' },
                { value: 'number', label: 'number' },
                { value: 'boolean', label: 'boolean' },
                { value: 'object', label: 'object' },
                { value: 'array', label: 'array' },
                { value: 'any', label: 'any' },
              ]}
              onChange={(v) => updateVar('headers', idx, { type: v as any })}
            />
            <Button size="small" onClick={() => removeVar('headers', idx)}>
              删除
            </Button>
          </Space>
        ))}
        <Button size="small" onClick={() => addVar('headers')}>
          新增 Header
        </Button>
      </div>

      <Divider style={{ margin: '12px 0' }} />

      <Text strong>Params</Text>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
        {(start.params || []).map((p, idx) => (
          <Space key={p.key || idx} align="baseline">
            <Input
              placeholder="param 名称"
              style={{ width: 140 }}
              value={p.name}
              onChange={(e) => updateVar('params', idx, { name: e.target.value })}
            />
            <Select
              style={{ width: 110 }}
              value={p.type}
              options={[
                { value: 'string', label: 'string' },
                { value: 'number', label: 'number' },
                { value: 'boolean', label: 'boolean' },
                { value: 'object', label: 'object' },
                { value: 'array', label: 'array' },
                { value: 'any', label: 'any' },
              ]}
              onChange={(v) => updateVar('params', idx, { type: v as any })}
            />
            <Button size="small" onClick={() => removeVar('params', idx)}>
              删除
            </Button>
          </Space>
        ))}
        <Button size="small" onClick={() => addVar('params')}>
          新增 Param
        </Button>
      </div>

      <Divider style={{ margin: '12px 0' }} />

      <Text strong>Body</Text>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
        {(start.body || []).map((b, idx) => (
          <Space key={b.key || idx} align="baseline">
            <Input
              placeholder="body 字段"
              style={{ width: 140 }}
              value={b.name}
              onChange={(e) => updateVar('body', idx, { name: e.target.value })}
            />
            <Select
              style={{ width: 110 }}
              value={b.type}
              options={[
                { value: 'string', label: 'string' },
                { value: 'number', label: 'number' },
                { value: 'boolean', label: 'boolean' },
                { value: 'object', label: 'object' },
                { value: 'array', label: 'array' },
                { value: 'any', label: 'any' },
              ]}
              onChange={(v) => updateVar('body', idx, { type: v as any })}
            />
            <Button size="small" onClick={() => removeVar('body', idx)}>
              删除
            </Button>
          </Space>
        ))}
        <Button size="small" onClick={() => addVar('body')}>
          新增 Body 字段
        </Button>
      </div>
    </div>
  );
}

// =============================================================================
// 3. Node Definition (Left Panel & Config)
// =============================================================================

export const inputNodeDefinition: NodeDefinition<'input'> = {
  type: 'input',
  label: '输入',
  category: '基础组件',
  buildNodeConfig: (data, position) => {
    return {
      id: `input`,
      shape: 'flow-start-card',
      x: position?.x ?? 60,
      y: position?.y ?? 40,
      data,
      attrs: {
        body: {
          stroke: '#d9d9d9',
          strokeWidth: 1,
          fill: '#ffffff',
        },
      },
    } as NodeMetadata;
  },
  getDisplayLabel: (data) => (data as any).name || '输入',
  renderProperties: (params) => <InputProperties data={params.data} onChange={params.onChange} />,
};

export function createInputData(): NodeData {
  return {
    kind: 'ui',
    nodeType: 'input',
    name: 'Request',
    start: {
      mode: 'request',
      method: 'GET',
      headers: [],
      params: [],
      body: [],
    },
  };
}

// =============================================================================
// 4. Shape Registration
// =============================================================================

let registered = false;

export function registerStartNode() {
  if (registered) return;
  registered = true;

  register({
    shape: 'flow-start-card',
    width: 260,
    height: 112,
    component: StartNode,
    ports: {
      groups: {
        right: {
          position: 'right',
          attrs: {
            circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' },
          },
        },
        left: {
          position: 'left',
          attrs: {
            circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' },
          },
        },
        manual: {
          position: 'absolute',
          attrs: {
            circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' },
          },
        },
      },
      items: [],
    },
  });
}

import React from 'react';
import { Button, Collapse, Tooltip, theme } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import { Graph, Dnd } from '@antv/x6';
import type { NodeType } from '../types';
import { createDefaultNodeData, getNodeDefinition, nodeDefinitions } from '../registry';

export type PaletteProps = {
  graph: Graph | null;
  dnd: Dnd | null;
  onQuickAdd: (type: NodeType) => void;
  canCreate: (type: NodeType) => { ok: boolean; reason?: string };
};

export default function Palette(props: PaletteProps) {
  const { graph, dnd, onQuickAdd, canCreate } = props;
  const { token } = theme.useToken();

  const items = React.useMemo(() => {
    const groups = new Map<string, Array<{ type: NodeType; label: string }>>();
    nodeDefinitions.forEach((d) => {
      const list = groups.get(d.category) || [];
      list.push({ type: d.type, label: d.label });
      groups.set(d.category, list);
    });
    return Array.from(groups.entries());
  }, []);

  const handleMouseDown = React.useCallback(
    (e: React.MouseEvent, type: NodeType) => {
      if (!graph || !dnd) return;
      const allow = canCreate(type);
      if (!allow.ok) return;
      const data = createDefaultNodeData(type);
      const def = getNodeDefinition(type);
      if (!data || !def) return;
      const node = graph.createNode(def.buildNodeConfig(data));
      dnd.start(node, e.nativeEvent);
    },
    [canCreate, dnd, graph],
  );

  return (
    <div style={{ width: 240, height: '100%', background: '#ffffff', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
      <Collapse
        ghost
        defaultActiveKey={items.map(([key]) => key)}
        items={items.map(([category, list]) => ({
          key: category,
          label: <span style={{ fontWeight: 600 }}>{category}</span>,
          children: (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {list.map((item) => {
                const allow = canCreate(item.type);
                return (
                  <div
                    key={item.type}
                    style={{
                      border: `1px solid ${token.colorBorder}`,
                      borderRadius: 4,
                      padding: '8px 4px',
                      cursor: allow.ok ? 'grab' : 'not-allowed',
                      userSelect: 'none',
                      background: allow.ok ? '#f8f9fb' : '#f5f5f5',
                      opacity: allow.ok ? 1 : 0.6,
                      textAlign: 'center',
                      fontSize: 12,
                      position: 'relative',
                    }}
                    onMouseDown={(e) => handleMouseDown(e, item.type)}
                    onDoubleClick={() => (allow.ok ? onQuickAdd(item.type) : undefined)}
                  >
                    <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {item.label}
                    </div>
                    {allow.ok && (
                       <div style={{ position: 'absolute', top: 2, right: 2 }}>
                          <Tooltip title="拖拽到画布创建；双击快速添加" placement="right">
                             <InfoCircleOutlined style={{ fontSize: 10, color: token.colorTextSecondary }} />
                          </Tooltip>
                       </div>
                    )}
                  </div>
                );
              })}
            </div>
          ),
        }))}
      />
    </div>
  );
}

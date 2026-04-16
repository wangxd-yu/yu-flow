import React, { useCallback, useState, useMemo, useEffect } from 'react';
import {
  Table, Input, Select, Switch, Button, Space, Tooltip, Popover,
  Form, InputNumber, Divider, Tag,
} from 'antd';
import {
  PlusOutlined, SubnodeOutlined, DeleteOutlined, SettingOutlined,
  HolderOutlined,
  CaretDownOutlined, CaretRightOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { SchemaNode, SchemaType } from './types';
import useSchemaDrawer from './useSchemaDrawer';

// ── dnd-kit ─────────────────────────────────────────────────
import {
  DndContext, closestCenter, PointerSensor, useSensor, useSensors,
  type DragEndEvent, DragOverlay,
} from '@dnd-kit/core';
import {
  SortableContext, useSortable, verticalListSortingStrategy,
} from '@dnd-kit/sortable';

// ═══════════════════════════════════════════════════════════════
// Props
// ═══════════════════════════════════════════════════════════════

export interface SchemaTreeTableProps {
  value: SchemaNode[];
  onChange: (value: SchemaNode[]) => void;
  /** 平铺模式：禁 object/array 和子节点 */
  flat?: boolean;
  /** 是否隐藏工具栏（预览/导入按钮由外部控制） */
  hideToolbar?: boolean;
  /** 隐藏「必填」列（如 Path 参数始终必填，无需展示） */
  hideRequired?: boolean;
  /** 隐藏底部「添加字段」按钮（如 Path 参数仅由 URL 自动生成） */
  hideAddButton?: boolean;
  /** 隐藏「操作」列（如 Path 参数不允许手动增删） */
  hideActions?: boolean;
}

// ═══════════════════════════════════════════════════════════════
// 常量 & 工具
// ═══════════════════════════════════════════════════════════════

const ROOT_ID = 'root';
let idCounter = 0;
const genId = (): string => `node_${Date.now()}_${++idCounter}`;

const createEmptyNode = (): SchemaNode => ({
  id: genId(), name: '', type: 'string', required: false, description: '',
});

const createRootNode = (): SchemaNode => ({
  id: ROOT_ID, name: '根节点', type: 'object', required: false, description: '', children: [],
});

const isRoot = (n: SchemaNode) => n.id === ROOT_ID;

// ═══════════════════════════════════════════════════════════════
// 树形递归操作（纯函数）
// ═══════════════════════════════════════════════════════════════

const updateNodeById = (
  nodes: SchemaNode[], id: string, patch: Partial<SchemaNode>,
): SchemaNode[] =>
  nodes.map((n) => {
    if (n.id === id) {
      const u = { ...n, ...patch };
      if (patch.type && patch.type !== 'object' && patch.type !== 'array') delete u.children;
      return u;
    }
    return n.children?.length ? { ...n, children: updateNodeById(n.children, id, patch) } : n;
  });

const removeNodeById = (nodes: SchemaNode[], id: string): SchemaNode[] =>
  nodes.filter((n) => n.id !== id)
    .map((n) => n.children?.length ? { ...n, children: removeNodeById(n.children, id) } : n);

const addSiblingAfter = (nodes: SchemaNode[], targetId: string, nn: SchemaNode): SchemaNode[] => {
  const r: SchemaNode[] = [];
  for (const n of nodes) {
    if (n.id === targetId) { r.push(n, nn); }
    else { r.push(n.children?.length ? { ...n, children: addSiblingAfter(n.children, targetId, nn) } : n); }
  }
  return r;
};

const prependChildNode = (nodes: SchemaNode[], parentId: string, nn: SchemaNode): SchemaNode[] =>
  nodes.map((n) => {
    if (n.id === parentId) return { ...n, children: [nn, ...(n.children || [])] };
    return n.children?.length ? { ...n, children: prependChildNode(n.children, parentId, nn) } : n;
  });

/**
 * 树形同级拖拽重排：仅同一 parent 下的兄弟节点可交换位置。
 */
const reorderInTree = (
  nodes: SchemaNode[], activeId: string, overId: string,
): SchemaNode[] => {
  const activeIdx = nodes.findIndex((n) => n.id === activeId);
  const overIdx = nodes.findIndex((n) => n.id === overId);
  if (activeIdx >= 0 && overIdx >= 0 && activeIdx !== overIdx) {
    const copy = [...nodes];
    const [removed] = copy.splice(activeIdx, 1);
    copy.splice(overIdx, 0, removed);
    return copy;
  }
  return nodes.map((n) =>
    n.children?.length ? { ...n, children: reorderInTree(n.children, activeId, overId) } : n,
  );
};

// ═══════════════════════════════════════════════════════════════
// 选项
// ═══════════════════════════════════════════════════════════════

const TYPE_OPTIONS: { label: string; value: SchemaType }[] = [
  { label: 'string', value: 'string' },
  { label: 'number', value: 'number' },
  { label: 'boolean', value: 'boolean' },
  { label: 'object', value: 'object' },
  { label: 'array', value: 'array' },
];

const FORMAT_OPTIONS = [
  { label: '无', value: '' },
  { label: 'date-time', value: 'date-time' },
  { label: 'date', value: 'date' },
  { label: 'email', value: 'email' },
  { label: 'uri', value: 'uri' },
  { label: 'ipv4', value: 'ipv4' },
  { label: 'ipv6', value: 'ipv6' },
  { label: 'uuid', value: 'uuid' },
];

// ═══════════════════════════════════════════════════════════════
// 高级设置面板
// ═══════════════════════════════════════════════════════════════

const AdvancedSettingsPanel: React.FC<{ record: SchemaNode; onPatch: (p: Partial<SchemaNode>) => void }> = ({ record, onPatch }) => {
  const isStr = record.type === 'string';
  const isNum = record.type === 'number';
  const isArr = record.type === 'array';
  return (
    <div style={{ width: 320 }}>
      <Form layout="vertical" size="small">
        {/* ── 通用字段 ── */}
        <Form.Item label="Mock" style={{ marginBottom: 8 }}>
          <Input placeholder="@name, @email" value={record.mock ?? ''} onChange={(e) => onPatch({ mock: e.target.value || undefined })} />
        </Form.Item>
        <Form.Item label="默认值" style={{ marginBottom: 8 }}>
          <Input placeholder="默认值" value={(record.defaultValue as string) ?? ''} onChange={(e) => onPatch({ defaultValue: e.target.value || undefined })} />
        </Form.Item>
        <Form.Item label="枚举值" style={{ marginBottom: 8 }} tooltip="逗号分隔">
          <Select mode="tags" placeholder="回车添加" value={record.enum ?? []} onChange={(v) => onPatch({ enum: v.length ? v : undefined })} tokenSeparators={[',']} style={{ width: '100%' }} />
        </Form.Item>

        {/* ── string 校验 ── */}
        {isStr && (
          <>
            <Divider style={{ margin: '6px 0' }} />
            <Form.Item label="Format" style={{ marginBottom: 8 }}>
              <Select value={record.format ?? ''} options={FORMAT_OPTIONS} onChange={(v) => onPatch({ format: v || undefined })} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="正则 (pattern)" style={{ marginBottom: 8 }}>
              <Input placeholder="^[a-zA-Z]+$" value={record.pattern ?? ''} onChange={(e) => onPatch({ pattern: e.target.value || undefined })} />
            </Form.Item>
            <Space>
              <Form.Item label="最小长度" style={{ marginBottom: 0 }}>
                <InputNumber min={0} value={record.minLength} onChange={(v) => onPatch({ minLength: v ?? undefined })} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item label="最大长度" style={{ marginBottom: 0 }}>
                <InputNumber min={0} value={record.maxLength} onChange={(v) => onPatch({ maxLength: v ?? undefined })} style={{ width: 100 }} />
              </Form.Item>
            </Space>
          </>
        )}

        {/* ── number 校验 ── */}
        {isNum && (
          <>
            <Divider style={{ margin: '6px 0' }} />
            <Space>
              <Form.Item label="最小值" style={{ marginBottom: 8 }}>
                <InputNumber value={record.minimum} onChange={(v) => onPatch({ minimum: v ?? undefined })} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item label="排他" style={{ marginBottom: 8 }}>
                <Switch size="small" checked={record.exclusiveMinimum ?? false} onChange={(v) => onPatch({ exclusiveMinimum: v || undefined })} />
              </Form.Item>
            </Space>
            <Space>
              <Form.Item label="最大值" style={{ marginBottom: 8 }}>
                <InputNumber value={record.maximum} onChange={(v) => onPatch({ maximum: v ?? undefined })} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item label="排他" style={{ marginBottom: 8 }}>
                <Switch size="small" checked={record.exclusiveMaximum ?? false} onChange={(v) => onPatch({ exclusiveMaximum: v || undefined })} />
              </Form.Item>
            </Space>
            <Form.Item label="倍数 (multipleOf)" style={{ marginBottom: 0 }}>
              <InputNumber min={0} value={record.multipleOf} onChange={(v) => onPatch({ multipleOf: v ?? undefined })} style={{ width: '100%' }} placeholder="如 0.01" />
            </Form.Item>
          </>
        )}

        {/* ── array 校验 ── */}
        {isArr && (
          <>
            <Divider style={{ margin: '6px 0' }} />
            <Space>
              <Form.Item label="最少元素" style={{ marginBottom: 8 }}>
                <InputNumber min={0} value={record.minItems} onChange={(v) => onPatch({ minItems: v ?? undefined })} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item label="最多元素" style={{ marginBottom: 8 }}>
                <InputNumber min={0} value={record.maxItems} onChange={(v) => onPatch({ maxItems: v ?? undefined })} style={{ width: 100 }} />
              </Form.Item>
            </Space>
            <Form.Item label="元素唯一 (uniqueItems)" style={{ marginBottom: 0 }}>
              <Switch size="small" checked={record.uniqueItems ?? false} onChange={(v) => onPatch({ uniqueItems: v || undefined })} />
            </Form.Item>
          </>
        )}
      </Form>
    </div>
  );
};

const getAdvancedTags = (r: SchemaNode): string[] => {
  const t: string[] = [];
  if (r.format) t.push(r.format);
  if (r.enum?.length) t.push(`枚举(${r.enum.length})`);
  if (r.pattern) t.push('正则');
  if (r.minLength !== undefined || r.maxLength !== undefined) t.push('长度');
  if (r.minimum !== undefined || r.maximum !== undefined) t.push('范围');
  if (r.multipleOf !== undefined) t.push('倍数');
  if (r.minItems !== undefined || r.maxItems !== undefined) t.push('数量');
  if (r.uniqueItems) t.push('唯一');
  if (r.mock) t.push('Mock');
  return t;
};

// ═══════════════════════════════════════════════════════════════
// dnd-kit 可拖拽行 + 手柄
// ═══════════════════════════════════════════════════════════════

/** 拖拽手柄 listeners 通过 Context 传递给 DragHandle 图标 */
const DragHandleContext = React.createContext<Record<string, any>>({});

interface SortableRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  'data-row-key'?: string;
}

const SortableRow: React.FC<SortableRowProps> = (props) => {
  const id = props['data-row-key'] ?? '';
  const {
    attributes, listeners, setNodeRef, transform, transition, isDragging,
  } = useSortable({
    id,
    // 禁用自动动画，避免抖动
    transition: { duration: 200, easing: 'ease' },
  });

  const style: React.CSSProperties = {
    ...props.style,
    // 只使用 translateY，不做 scaleX/scaleY，消除抖动
    transform: transform ? `translateY(${Math.round(transform.y)}px)` : undefined,
    transition,
    ...(isDragging ? { position: 'relative' as const, zIndex: 999, opacity: 0.85, boxShadow: '0 2px 10px rgba(0,0,0,0.15)' } : {}),
  };

  return (
    <DragHandleContext.Provider value={id === ROOT_ID ? {} : (listeners ?? {})}>
      <tr {...props} ref={setNodeRef} style={style} {...attributes} />
    </DragHandleContext.Provider>
  );
};

/** 拖拽手柄图标：仅在行 hover 时显示 */
const DragHandle: React.FC = () => {
  const listeners = React.useContext(DragHandleContext);
  const hasListeners = Object.keys(listeners).length > 0;
  if (!hasListeners) return null;
  return (
    <span className="stt-drag-handle" {...listeners}>
      <HolderOutlined style={{ cursor: 'grab', color: '#bfbfbf', fontSize: 12 }} />
    </span>
  );
};

// ═══════════════════════════════════════════════════════════════
// 收集所有节点 id（递归展平）
// ═══════════════════════════════════════════════════════════════

const collectIds = (nodes: SchemaNode[]): string[] => {
  const ids: string[] = [];
  for (const n of nodes) {
    ids.push(n.id);
    if (n.children?.length) ids.push(...collectIds(n.children));
  }
  return ids;
};

// ═══════════════════════════════════════════════════════════════
// CSS
// ═══════════════════════════════════════════════════════════════

const SCHEMA_TABLE_CSS = `
  .stt-table .ant-table-tbody > tr > td { padding: 2px 4px !important; }
  .stt-table .ant-table-tbody > tr > td.stt-column-name { padding-right: 0 !important; }
  .stt-table .ant-table-thead > tr > th { padding: 4px 8px !important; font-size: 12px; }

  /* ── 消除折行，撑满第一列：使用左浮动 + BFC ── */
  .stt-table .ant-table-row-indent,
  .stt-expand-icon-wrapper {
    float: left;
    height: 22px;
  }
  .stt-name-wrapper {
    display: flex;
    align-items: center;
    /* Flex 容器自带 BFC 属性，填满除去浮动元素的剩余空间 */
    width: auto;
  }

  /* ── 根节点行 ── */
  .stt-table .schema-root-row > td { background: #fafafa !important; }

  /* ── 行 hover 整行淡蓝背景（Apifox 风格） ── */
  .stt-table .ant-table-tbody > tr:hover > td {
    background: #f0f7ff !important;
  }
  .stt-table .schema-root-row:hover > td {
    background: #f0f4fa !important;
  }

  /* ── 拖拽手柄：默认隐藏，行 hover 时显示 ── */
  .stt-drag-handle {
    display: inline-flex;
    align-items: center;
    opacity: 0;
    transition: opacity 0.15s;
    margin-right: 4px;
    height: 22px;
  }
  .stt-table .ant-table-tbody > tr:hover .stt-drag-handle {
    opacity: 1;
  }

  /* ── 展开图标区域修正 ── */
  .stt-table .ant-table-row-expand-icon {
    display: none !important;  /* 隐藏默认 +/- 图标，用自定义三角替代 */
  }

  /* ── Focus / Hover 高亮 ── */
  .stt-cell-input {
    border: 1px solid transparent !important;
    border-radius: 4px !important;
    transition: border-color 0.2s, box-shadow 0.2s, background 0.15s !important;
    background: transparent !important;
  }
  .stt-cell-input:hover {
    border-color: #d9d9d9 !important;
    background: #fff !important;
  }
  .stt-cell-input:focus,
  .stt-cell-input.ant-input-focused {
    border-color: #1677ff !important;
    box-shadow: 0 0 0 2px rgba(22,119,255,0.15) !important;
    background: #fff !important;
  }

  /* Select 版本 */
  .stt-cell-select .ant-select-selector {
    border: 1px solid transparent !important;
    border-radius: 4px !important;
    transition: border-color 0.2s, box-shadow 0.2s, background 0.15s !important;
    background: transparent !important;
    padding: 0 4px !important;
    min-height: 22px !important;
    height: 22px !important;
  }
  .stt-cell-select:hover .ant-select-selector {
    border-color: #d9d9d9 !important;
    background: #fff !important;
  }
  .stt-cell-select.ant-select-focused .ant-select-selector,
  .stt-cell-select.ant-select-open .ant-select-selector {
    border-color: #1677ff !important;
    box-shadow: 0 0 0 2px rgba(22,119,255,0.15) !important;
    background: #fff !important;
  }
`;

const CELL_INPUT_STYLE: React.CSSProperties = {
  fontFamily: 'monospace', fontSize: 12, padding: '1px 4px',
  minHeight: 22, lineHeight: '22px',
};

// ═══════════════════════════════════════════════════════════════
// 主组件
// ═══════════════════════════════════════════════════════════════

const SchemaTreeTable: React.FC<SchemaTreeTableProps> = ({
  value, onChange, flat = false, hideToolbar = false, hideRequired = false, hideAddButton = false, hideActions = false,
}) => {
  const [openPopoverId, setOpenPopoverId] = useState<string | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);

  // ── dnd-kit 传感器（distance:8 防止误触） ────────────────
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
  );

  // ── 确保有根节点 ─────────────────────────────────────────
  const dataSource = useMemo<SchemaNode[]>(() => {
    if (flat) return value;
    if (value.length === 1 && value[0].id === ROOT_ID) return value;
    const root = createRootNode();
    root.children = value.length > 0 ? value : [];
    return [root];
  }, [value, flat]);

  const emitChange = useCallback((next: SchemaNode[]) => onChange(next), [onChange]);

  const allIds = useMemo(() => collectIds(dataSource), [dataSource]);

  // ── 拖拽事件 ─────────────────────────────────────────────
  const handleDragStart = useCallback((event: any) => {
    setActiveId(String(event.active.id));
  }, []);

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    setActiveId(null);
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const aid = String(active.id);
    const oid = String(over.id);
    if (aid === ROOT_ID) return;
    emitChange(reorderInTree(dataSource, aid, oid));
  }, [dataSource, emitChange]);

  const handleDragCancel = useCallback(() => setActiveId(null), []);

  // ── 节点操作 ─────────────────────────────────────────────

  const handleFieldChange = useCallback(
    (id: string, field: keyof SchemaNode, val: any) => emitChange(updateNodeById(dataSource, id, { [field]: val })),
    [dataSource, emitChange],
  );

  const handleNodePatch = useCallback(
    (id: string, patch: Partial<SchemaNode>) => emitChange(updateNodeById(dataSource, id, patch)),
    [dataSource, emitChange],
  );

  const focusNewNode = (newId: string) => {
    setTimeout(() => document.getElementById(`input-name-${newId}`)?.focus(), 50);
  };

  const handleAddSibling = useCallback((id: string) => {
    const nn = createEmptyNode();
    emitChange(addSiblingAfter(dataSource, id, nn));
    focusNewNode(nn.id);
  }, [dataSource, emitChange]);

  const handleAddChild = useCallback((id: string) => {
    const nn = createEmptyNode();
    emitChange(prependChildNode(dataSource, id, nn));
    // 强制展开父节点
    setExpandedRowKeys((keys) => Array.from(new Set([...keys, id])));
    focusNewNode(nn.id);
  }, [dataSource, emitChange]);

  const handleRemove = useCallback((id: string) => emitChange(removeNodeById(dataSource, id)), [dataSource, emitChange]);

  // ── 展开状态管理 ──────────────────────────────────────────
  const [expandedRowKeys, setExpandedRowKeys] = useState<readonly React.Key[]>([]);

  // 同步初始化展开（默认全部展开）
  useEffect(() => {
    setExpandedRowKeys(allIds);
  }, [allIds]);

  const onExpand = (expanded: boolean, record: SchemaNode) => {
    const key = record.id;
    if (expanded) {
      setExpandedRowKeys((keys) => [...keys, key]);
    } else {
      setExpandedRowKeys((keys) => keys.filter((k) => k !== key));
    }
  };

  // ── Schema 预览 / 导入（复用 Hook） ────────────────────────
  const { previewBtn, importBtn, drawers: schemaDrawers } = useSchemaDrawer({
    nodes: dataSource,
    onNodesChange: emitChange,
  });

  // ═══════════════════════════════════════════════════════════
  // 自定义展开图标：▶ / ▼ 三角
  // ═══════════════════════════════════════════════════════════

  const customExpandIcon = useCallback(({ expanded, onExpand, record }: any) => {
    if (!record.children?.length) {
      // 无子节点时占位，保证缩进对齐 (14px 是新图标宽度)
      return <span className="stt-expand-icon-wrapper" style={{ display: 'inline-block', width: 14, flexShrink: 0 }} />;
    }
    return (
      <span
        className="stt-expand-icon-wrapper"
        onClick={(e: any) => onExpand(record, e)}
        style={{
          cursor: 'pointer',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 14,
          height: 22,
          color: '#8c8c8c',
          fontSize: 10,
          transition: 'color 0.2s',
          flexShrink: 0,
        }}
      >
        {expanded ? <CaretDownOutlined /> : <CaretRightOutlined />}
      </span>
    );
  }, []);

  // ═══════════════════════════════════════════════════════════
  // 列定义
  // ═══════════════════════════════════════════════════════════

  const columns: ColumnsType<SchemaNode> = [
    // ── 参数名（拖拽手柄 + 输入框，BFC 自适应撑满右侧） ──
    {
      title: '参数名',
      dataIndex: 'name',
      key: 'name',
      width: 250,
      className: 'stt-column-name',
      render: (_, record) => {
        if (isRoot(record)) {
          return (
            <div className="stt-name-wrapper" style={{ fontWeight: 600, fontSize: 12, color: '#595959' }}>
              <span>{record.name}</span>
            </div>
          );
        }
        return (
          <div className="stt-name-wrapper" style={{ gap: 4 }}>
            <DragHandle />
            <Input
              id={`input-name-${record.id}`}
              size="small"
              variant="borderless"
              value={record.name}
              placeholder="fieldName"
              onChange={(e) => handleFieldChange(record.id, 'name', e.target.value)}
              className="stt-cell-input"
              style={{ ...CELL_INPUT_STYLE, flex: 1, minWidth: 0, paddingRight: 4 }}
            />
          </div>
        );
      },
    },

    // ── 中文名 ──────────────────────────────────────────────
    {
      title: '中文名',
      dataIndex: 'title',
      key: 'title',
      width: 130,
      render: (_, record) =>
        isRoot(record) ? null : (
          <Input
            size="small" variant="borderless"
            value={record.title ?? ''} placeholder="显示名"
            onChange={(e) => handleFieldChange(record.id, 'title', e.target.value || undefined)}
            className="stt-cell-input"
            style={{ ...CELL_INPUT_STYLE, fontFamily: 'inherit' }}
          />
        ),
    },

    // ── 类型 + 高级标签 ─────────────────────────────────────
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 200,
      render: (_, record) => {
        if (isRoot(record)) {
          return <Tag color="purple" style={{ margin: 0, fontSize: 11 }}>object</Tag>;
        }
        const advTags = getAdvancedTags(record);
        return (
          <Space size={2} style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap' }}>
            <Select
              size="small" variant="borderless" className="stt-cell-select"
              value={record.type}
              options={flat ? TYPE_OPTIONS.filter((o) => o.value !== 'object' && o.value !== 'array') : TYPE_OPTIONS}
              onChange={(v) => handleFieldChange(record.id, 'type', v)}
              style={{ width: 85, fontSize: 12 }}
            />
            <Popover trigger="click" placement="bottomLeft"
              open={openPopoverId === record.id}
              onOpenChange={(vis) => setOpenPopoverId(vis ? record.id : null)}
              title="高级设置"
              content={<AdvancedSettingsPanel record={record} onPatch={(p) => handleNodePatch(record.id, p)} />}
            >
              <Tooltip title="高级设置">
                <Button type="text" size="small" icon={<SettingOutlined style={{ fontSize: 12 }} />}
                  style={{ color: advTags.length > 0 ? '#1677ff' : '#bfbfbf', padding: '0 2px', height: 22, width: 22 }} />
              </Tooltip>
            </Popover>
            {advTags.map((tag) => (
              <Tag key={tag} color="blue" style={{ margin: 0, fontSize: 10, lineHeight: '16px', padding: '0 3px' }}>{tag}</Tag>
            ))}
          </Space>
        );
      },
    },

    // ── 必填 ────────────────────────────────────────────────
    ...(!hideRequired ? [{
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      width: 60,
      align: 'center' as const,
      render: (_: any, record: SchemaNode) =>
        isRoot(record) ? null : (
          <Switch
            size="small"
            checked={record.required}
            onChange={(v) => handleFieldChange(record.id, 'required', v)}
          />
        ),
    }] : []),

    // ── Mock ────────────────────────────────────────────────
    {
      title: 'Mock',
      dataIndex: 'mock',
      key: 'mock',
      width: 130,
      render: (_, record) =>
        isRoot(record) ? null : (
          <Input
            size="small" variant="borderless"
            value={record.mock ?? ''} placeholder="@mock"
            onChange={(e) => handleFieldChange(record.id, 'mock', e.target.value || undefined)}
            className="stt-cell-input"
            style={{ ...CELL_INPUT_STYLE, color: '#8c8c8c' }}
          />
        ),
    },

    // ── 说明 ────────────────────────────────────────────────
    {
      title: '说明',
      dataIndex: 'description',
      key: 'description',
      render: (_, record) =>
        isRoot(record) ? (
          <span style={{ fontSize: 12, color: '#8c8c8c' }}>JSON 根对象</span>
        ) : (
          <Input
            size="small" variant="borderless"
            value={record.description} placeholder="字段说明"
            onChange={(e) => handleFieldChange(record.id, 'description', e.target.value)}
            className="stt-cell-input"
            style={{ ...CELL_INPUT_STYLE, fontFamily: 'inherit' }}
          />
        ),
    },

    // ── 操作 ────────────────────────────────────────────────
    ...(!hideActions ? [{
      title: '操作',
      key: 'actions',
      width: flat ? 80 : 100,
      align: 'center' as const,
      render: (_: any, record: SchemaNode) => {
        if (isRoot(record)) {
          return (
            <Tooltip title="添加子节点">
              <Button type="text" size="small" icon={<SubnodeOutlined style={{ fontSize: 12 }} />}
                onClick={() => handleAddChild(record.id)} style={{ padding: '0 4px', height: 22 }} />
            </Tooltip>
          );
        }
        return (
          <Space size={0}>
            <Tooltip title="添加同级">
              <Button type="text" size="small" icon={<PlusOutlined style={{ fontSize: 11 }} />}
                onClick={() => handleAddSibling(record.id)} style={{ padding: '0 3px', height: 22 }} />
            </Tooltip>
            {!flat && record.type === 'object' && (
              <Tooltip title="添加子节点">
                <Button type="text" size="small" icon={<SubnodeOutlined style={{ fontSize: 11 }} />}
                  onClick={() => handleAddChild(record.id)} style={{ padding: '0 3px', height: 22 }} />
              </Tooltip>
            )}
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined style={{ fontSize: 11 }} />}
                onClick={() => handleRemove(record.id)} style={{ padding: '0 3px', height: 22 }} />
            </Tooltip>
          </Space>
        );
      },
    }] : []),
  ];

  // ═══════════════════════════════════════════════════════════
  // 渲染
  // ═══════════════════════════════════════════════════════════

  return (
    <div>
      <style>{SCHEMA_TABLE_CSS}</style>

      {/* 工具栏（仅非 flat 且未隐藏时显示） */}
      {!flat && !hideToolbar && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 6, gap: 6 }}>
          {previewBtn}
          {importBtn}
        </div>
      )}

      {/* dnd-kit 上下文 */}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        onDragCancel={handleDragCancel}
      >
        <SortableContext items={allIds} strategy={verticalListSortingStrategy}>
          <Table<SchemaNode>
            className="stt-table"
            columns={columns}
            dataSource={dataSource}
            rowKey="id"
            pagination={false}
            size="small"
            bordered
            expandable={{
              expandedRowKeys,
              onExpand,
              expandIcon: customExpandIcon,
              indentSize: 12, // 较小的缩进
            }}
            locale={{ emptyText: '暂无字段' }}
            style={{ fontSize: 12 }}
            rowClassName={(record) => isRoot(record) ? 'schema-root-row' : ''}
            components={{ body: { row: SortableRow } }}
          />
        </SortableContext>
        {/* DragOverlay 防止拖拽时原位元素闪烁 */}
        <DragOverlay dropAnimation={null}>
          {activeId ? (
            <div style={{
              background: '#e6f4ff', border: '1px solid #91caff', borderRadius: 4,
              padding: '4px 12px', fontSize: 12, fontFamily: 'monospace', opacity: 0.9,
              boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
            }}>
              拖拽中...
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>

      {/* flat 模式底部添加按钮 */}
      {flat && !hideAddButton && (
        <Button type="dashed" block size="small" icon={<PlusOutlined />}
          style={{ marginTop: 4, height: 28, fontSize: 12 }}
          onClick={() => { const nn = createEmptyNode(); emitChange([...dataSource, nn]); focusNewNode(nn.id); }}
        >
          添加字段
        </Button>
      )}

      {/* Schema 预览 / 导入 Drawers */}
      {schemaDrawers}
    </div>
  );
};

export default SchemaTreeTable;

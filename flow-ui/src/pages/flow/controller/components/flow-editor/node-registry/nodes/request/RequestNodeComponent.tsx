// ============================================================================
// RequestNodeComponent.tsx — V3.2 Request 节点
// 使用公共 NodeHeader + useNodeSelection
// 新增：右上角 Method 选择器，根据 Method 控制 Body 出口可见性
// ============================================================================

import React from 'react';
import ReactDOM from 'react-dom';
import { Node } from '@antv/x6';
import {
    useNodeSelection, NodeHeader, NodeWrapper, NodeToolbar, getNodeTheme, ResizeHandle, NODE_HEADER_WITH_ID_HEIGHT,
} from '../../shared/useNodeSelection';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import {
    COMPACT_ACCENT_WIDTH,
    COMPACT_NODE_WIDTH,
    CompactExitLabels,
    compactExitPortY,
    getGraphNodeViewMode,
    multiExitCompactFooterHeight,
    useCompactNodeResize,
} from '../../shared/NodeViewMode';

const ICONS = {
    request: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M140 160h744v72H140zM140 376h744v72H140zM140 592h744v72H140zM140 808h744v72H140z" />
        </svg>
    ),
};

// ── HTTP 方法选项 ──
const METHOD_OPTIONS = [
    { value: 'GET', label: 'GET', color: '#52c41a' },
    { value: 'POST', label: 'POST', color: '#fa8c16' },
    { value: 'PUT', label: 'PUT', color: '#1677ff' },
    { value: 'DELETE', label: 'DELETE', color: '#f5222d' },
    { value: 'PATCH', label: 'PATCH', color: '#722ed1' },
];

/** 判断该 method 是否支持 body */
export function methodHasBody(method?: string): boolean {
    const m = (method || 'GET').toUpperCase();
    return m === 'POST' || m === 'PUT' || m === 'PATCH';
}

// ── 布局常量，与端口定位共享 ──
export const REQUEST_LAYOUT = {
    headerHeight: NODE_HEADER_WITH_ID_HEIGHT,
    paddingTop: 10,
    rowHeight: 20,
    rowGap: 2,
    paddingBottom: 10,
    width: 260,
    rowCenterY: (index: number) => NODE_HEADER_WITH_ID_HEIGHT + 10 + index * (20 + 2) + 10,
    get totalHeight2() { return NODE_HEADER_WITH_ID_HEIGHT + 10 + 2 * 22 + 10; },
    get totalHeight3() { return NODE_HEADER_WITH_ID_HEIGHT + 10 + 3 * 22 + 10; },
    totalHeight: 40 + 10 + 3 * 22 + 10,                     // default (3 rows)
};

// ── Method 下拉选择组件 (嵌入 Header extra，使用 Portal 渲染下拉) ──
const MethodSelect: React.FC<{ value: string; onChange: (v: string) => void }> = ({ value, onChange }) => {
    const [open, setOpen] = React.useState(false);
    const triggerRef = React.useRef<HTMLDivElement>(null);
    const dropdownRef = React.useRef<HTMLDivElement>(null);
    const [pos, setPos] = React.useState<{ top: number; left: number }>({ top: 0, left: 0 });

    const current = METHOD_OPTIONS.find(o => o.value === value) || METHOD_OPTIONS[0];

    // 计算下拉位置 & 关闭处理
    React.useEffect(() => {
        if (!open) return;

        // 计算触发按钮位置
        if (triggerRef.current) {
            const rect = triggerRef.current.getBoundingClientRect();
            setPos({
                top: rect.bottom + 4,
                left: rect.right - 76, // 右对齐，dropdown minWidth=76
            });
        }

        const handler = (e: MouseEvent) => {
            const target = e.target as HTMLElement;
            if (triggerRef.current?.contains(target)) return;
            if (dropdownRef.current?.contains(target)) return;
            setOpen(false);
        };
        document.addEventListener('mousedown', handler);
        return () => document.removeEventListener('mousedown', handler);
    }, [open]);

    // 下拉面板通过 Portal 渲染到 document.body
    const dropdown = open ? ReactDOM.createPortal(
        <div
            ref={dropdownRef}
            onMouseDown={(e) => e.stopPropagation()}
            style={{
                position: 'fixed',
                top: pos.top,
                left: pos.left,
                background: '#ffffff',
                borderRadius: 6,
                boxShadow: '0 3px 6px -4px rgba(0,0,0,0.12), 0 6px 16px 0 rgba(0,0,0,0.08)',
                border: '1px solid #f0f0f0',
                overflow: 'hidden',
                zIndex: 99999,
                minWidth: 76,
            }}
        >
            {METHOD_OPTIONS.map((opt) => (
                <div
                    key={opt.value}
                    onClick={(e) => {
                        e.stopPropagation();
                        onChange(opt.value);
                        setOpen(false);
                    }}
                    style={{
                        padding: '4px 10px',
                        fontSize: 11,
                        fontWeight: 600,
                        color: opt.color,
                        cursor: 'pointer',
                        background: opt.value === value ? '#f5f5f5' : 'transparent',
                        transition: 'background 0.15s',
                    }}
                    onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = '#f5f5f5'; }}
                    onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = opt.value === value ? '#f5f5f5' : 'transparent'; }}
                >
                    {opt.label}
                </div>
            ))}
        </div>,
        document.body,
    ) : null;

    return (
        <div style={{ position: 'relative', pointerEvents: 'auto', userSelect: 'none' }}>
            {/* Trigger button */}
            <div
                ref={triggerRef}
                onClick={(e) => { e.stopPropagation(); setOpen(!open); }}
                onMouseDown={(e) => e.stopPropagation()}
                style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 3,
                    padding: '1px 6px',
                    fontSize: 10,
                    fontWeight: 700,
                    color: current.color,
                    background: '#ffffff',
                    border: `1px solid ${current.color}`,
                    borderRadius: 4,
                    cursor: 'pointer',
                    lineHeight: '16px',
                    whiteSpace: 'nowrap',
                }}
            >
                {current.label}
                <svg viewBox="0 0 1024 1024" width="8" height="8" fill="currentColor" style={{ opacity: 0.6, transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}>
                    <path d="M512 714.7L73.6 276.3c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0L512 624.1l393.1-393.1c12.5-12.5 32.8-12.5 45.3 0s12.5 32.8 0 45.3L512 714.7z" />
                </svg>
            </div>

            {/* Dropdown rendered via Portal */}
            {dropdown}
        </div>
    );
};

export const RequestNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = React.useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'orange');
    const { outlineCss, borderColor, selected } = useNodeSelection(node, { defaultColor: themeObj.primary, selectedColor: themeObj.primary, borderRadius: 10 });

    React.useEffect(() => {
        const onDataChange = () => setData({ ...node.getData() });
        node.on('change:data', onDataChange);
        return () => { node.off('change:data', onDataChange); };
    }, [node]);

    // ── 标题 ──
    const nodeLabel = data?.__label || 'Request';
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle });
    }, [node]);

    // ── Method ──
    const method = data?.method || 'GET';
    const hasBody = methodHasBody(method);
    const cardHeight = hasBody ? REQUEST_LAYOUT.totalHeight3 : REQUEST_LAYOUT.totalHeight2;
    const exitCount = hasBody ? 3 : 2;
    const compactFooterH = multiExitCompactFooterHeight(exitCount);
    const compactHeight = REQUEST_LAYOUT.headerHeight + compactFooterH;

    const { isCompact } = useCompactNodeResize(node, {
        cardMinHeight: cardHeight,
        compactHeight,
        minWidth: REQUEST_LAYOUT.width,
        cardDefaultWidth: REQUEST_LAYOUT.width,
        compactWidth: COMPACT_NODE_WIDTH + COMPACT_ACCENT_WIDTH,
    });

    const syncRequestPorts = React.useCallback(() => {
        const w = node.getSize().width || REQUEST_LAYOUT.width;
        const h = node.getSize().height;
        const compact = getGraphNodeViewMode(node) === 'compact';
        const ports = hasBody
            ? (['headers', 'params', 'body'] as const)
            : (['headers', 'params'] as const);

        const footerTop = REQUEST_LAYOUT.headerHeight;
        ports.forEach((id, idx) => {
            const y = compact
                ? compactExitPortY(footerTop, idx)
                : REQUEST_LAYOUT.rowCenterY(idx);
            if (!node.hasPort(id)) {
                node.addPort({ id, group: 'absolute-out-solid', args: { x: w, y, dx: 0 } });
            } else {
                node.setPortProp(id, 'args', { x: w, y, dx: 0 });
            }
        });

        if (!hasBody && node.hasPort('body')) {
            const graph = node.model?.graph;
            if (graph) {
                graph.getConnectedEdges(node).forEach((edge: any) => {
                    if (edge.getSourcePortId?.() === 'body' && edge.getSourceCellId?.() === node.id) {
                        graph.removeEdge(edge);
                    }
                });
            }
            node.removePort('body');
        }

        // compact 时高度由 useCompactNodeResize 管；card 时按行数校正
        if (!compact) {
            const target = hasBody ? REQUEST_LAYOUT.totalHeight3 : REQUEST_LAYOUT.totalHeight2;
            if (Math.abs(h - target) > 0.5) {
                node.resize(Math.max(w, REQUEST_LAYOUT.width), target);
            }
        }
    }, [node, hasBody]);

    React.useEffect(() => {
        syncRequestPorts();
    }, [syncRequestPorts, isCompact]);

    const handleMethodChange = React.useCallback((newMethod: string) => {
        const oldData = node.getData() || {};
        node.setData({ ...oldData, method: newMethod });
    }, [node]);

    const rows = hasBody ? ['Headers', 'Params', 'Body'] : ['Headers', 'Params'];

    // Method 选择器作为 header extra
    const methodExtra = isCompact ? undefined : (
        <MethodSelect value={method} onChange={handleMethodChange} />
    );

    return (
        <NodeWrapper
            node={node} selected={selected} themeColor={themeObj.primary} outlineCss={outlineCss} backgroundColor={themeObj.bodyBg} extraStyle={{ borderRadius: 10, flexDirection: 'row' }}
        >
            <div style={{ width: 12, height: '100%', background: themeObj.primary, pointerEvents: 'auto' }} />
            {/* Content */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <NodeHeader
                    icon={ICONS.request}
                    title={nodeLabel}
                    theme={themeObj}
                    height={REQUEST_LAYOUT.headerHeight}
                    node={node}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                    extra={methodExtra}
                />

                {isCompact ? (
                    <CompactExitLabels
                        height={compactFooterH}
                        exits={rows.map((label) => ({
                            id: label.toLowerCase(),
                            label,
                        }))}
                    />
                ) : (
                    <div style={{ paddingTop: REQUEST_LAYOUT.paddingTop, paddingBottom: REQUEST_LAYOUT.paddingBottom, paddingRight: 12, display: 'flex', flexDirection: 'column', gap: REQUEST_LAYOUT.rowGap, pointerEvents: 'auto' }}>
                        {rows.map((label) => (
                            <div key={label} style={{ height: REQUEST_LAYOUT.rowHeight, display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
                                <span style={{ fontSize: 11, color: '#595959' }}>{label}</span>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {!isCompact && (
                <ResizeHandle
                    node={node}
                    minWidth={REQUEST_LAYOUT.width}
                    minHeight={cardHeight}
                    axes="x"
                    color={themeObj.primary}
                />
            )}
        </NodeWrapper>
    );
};

export default RequestNodeComponent;

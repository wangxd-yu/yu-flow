// ============================================================================
// useNodeSelection.ts
// 公共 Hook & 组件: 节点卡片选中态 + 统一 Header + 根容器样式 + ResizeHandle + Toolbar
// 所有 React Shape 节点共用
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import { Popover } from 'antd';
import {
    BugOutlined,
    SettingOutlined,
    InfoCircleOutlined,
    CopyOutlined,
    DeleteOutlined
} from '@ant-design/icons';
import { getNodeRegistration } from '../registry';
import type { DslNodeType } from '../../types';

/** 从节点 data / 注册表解析说明文案 */
export function resolveNodeDescription(node: Node, override?: string): string | undefined {
    if (override?.trim()) return override.trim();
    const type = (node.getData() as any)?.__dslType as DslNodeType | undefined;
    if (!type) return undefined;
    const text = getNodeRegistration(type)?.description;
    return text?.trim() || undefined;
}

function NodeInfoPopover({
    description,
    title,
    children,
    iconColor,
}: {
    description: string;
    title?: string;
    children: React.ReactElement;
    iconColor?: string;
}) {
    return React.createElement(
        Popover,
        {
            title: title || '节点说明',
            trigger: 'click',
            placement: 'rightTop',
            getPopupContainer: () => document.body,
            overlayStyle: { maxWidth: 320, zIndex: 10050 },
            content: React.createElement(
                'div',
                {
                    style: {
                        fontSize: 12,
                        lineHeight: 1.6,
                        color: '#595959',
                        whiteSpace: 'pre-wrap',
                        maxWidth: 280,
                    },
                    onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
                    onClick: (e: React.MouseEvent) => e.stopPropagation(),
                },
                description,
            ),
        },
        React.createElement(
            'span',
            {
                onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
                onClick: (e: React.MouseEvent) => e.stopPropagation(),
                style: {
                    display: 'inline-flex',
                    alignItems: 'center',
                    cursor: 'pointer',
                    color: iconColor || '#8c8c8c',
                    flexShrink: 0,
                },
                title: '查看说明',
            },
            children,
        ),
    );
}

// ═══════════════════════════════════════════════════════════════════
// 1. 选中态 Hook
// ═══════════════════════════════════════════════════════════════════

export interface NodeSelectionOpts {
    defaultColor?: string;
    selectedColor?: string;
    defaultWidth?: number;
    selectedWidth?: number;
    borderRadius?: number;
}

export interface NodeSelectionResult {
    selected: boolean;
    borderColor: string;
    borderWidth: number;
    outlineStyle: string;
    attrs: any;
    outlineCss: React.CSSProperties;
}

/** 卡片圆角（Postman 向） */
export const NODE_CARD_RADIUS = 10;

export function useNodeSelection(node: Node, opts: NodeSelectionOpts = {}): NodeSelectionResult {
    const {
        defaultColor = '#e5e7eb',
        selectedColor = '#1677ff',
        defaultWidth = 1,
        /** X6 选中描边通常 ≥3；勿降到 2，否则默认 attrs.strokeWidth:2 会被误判为选中 */
        selectedWidth = 3,
        borderRadius = NODE_CARD_RADIUS,
    } = opts;

    const [attrs, setAttrs] = React.useState(node.getAttrs());

    React.useEffect(() => {
        const handler = () => setAttrs({ ...node.getAttrs() });
        node.on('change:attrs', handler);
        return () => { node.off('change:attrs', handler); };
    }, [node]);

    const sw = (attrs?.body?.strokeWidth as number) ?? defaultWidth;
    const selected = sw >= selectedWidth;
    const borderColor = selected ? (attrs?.body?.stroke as string || selectedColor) : defaultColor;
    // 选中视觉用 2px（Postman 向），检测阈值仍用 selectedWidth
    const borderWidth = selected ? 2 : defaultWidth;

    return {
        selected,
        borderColor,
        borderWidth,
        attrs,
        outlineStyle: 'solid',
        // 发丝边 + 轻环境阴影；选中时略加强描边与光晕
        outlineCss: {
            border: `${borderWidth}px solid ${borderColor}`,
            borderRadius,
            boxShadow: selected
                ? `0 0 0 1px ${borderColor}28, 0 4px 16px rgba(15, 23, 42, 0.08)`
                : '0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 14px rgba(15, 23, 42, 0.05)',
        },
    };
}

// ═══════════════════════════════════════════════════════════════════
// 2. 节点主题色配置表
// ═══════════════════════════════════════════════════════════════════

export interface NodeTheme {
    primary: string;
    headerBg: string;
    headerBorder: string;
    titleColor: string;
    bodyBg: string;
}

export const NODE_THEMES: Record<string, NodeTheme> = {
    blue: {
        primary: '#1677ff', headerBg: '#e6f4ff', headerBorder: '#91caff', titleColor: '#0050b3', bodyBg: '#ffffff',
    },
    green: {
        primary: '#52c41a', headerBg: '#f6ffed', headerBorder: '#b7eb8f', titleColor: '#135200', bodyBg: '#ffffff',
    },
    orange: {
        primary: '#fa8c16', headerBg: '#fff7e6', headerBorder: '#ffd591', titleColor: '#873800', bodyBg: '#ffffff',
    },
    purple: {
        primary: '#722ed1', headerBg: '#f9f0ff', headerBorder: '#d3adf7', titleColor: '#391085', bodyBg: '#ffffff',
    },
    gray: {
        primary: '#595959', headerBg: '#fafafa', headerBorder: '#d9d9d9', titleColor: '#1f1f1f', bodyBg: '#ffffff',
    },
    red: {
        primary: '#f5222d', headerBg: '#fff1f0', headerBorder: '#ffa39e', titleColor: '#a8071a', bodyBg: '#ffffff',
    },
    magenta: {
        primary: '#eb2f96', headerBg: '#fff0f6', headerBorder: '#ffadd2', titleColor: '#c41d7f', bodyBg: '#ffffff',
    },
    cyan: {
        primary: '#13c2c2', headerBg: '#e6fffb', headerBorder: '#87e8de', titleColor: '#08979c', bodyBg: '#ffffff',
    },
    dark: {
        primary: '#1f1f1f', headerBg: '#f5f5f5', headerBorder: '#d9d9d9', titleColor: '#000000', bodyBg: '#ffffff',
    },
};

export const PALETTE_MAP: Record<string, string> = {
    '#1f1f1f': 'dark', '#1677ff': 'blue', '#2f54eb': 'blue', '#52c41a': 'green', '#fa8c16': 'orange',
    '#f5222d': 'red', '#722ed1': 'purple', '#eb2f96': 'magenta', '#13c2c2': 'cyan',
};

export function getNodeTheme(colorOrKey?: string): NodeTheme {
    if (!colorOrKey) return NODE_THEMES.gray;
    // Check if it's a key
    if (NODE_THEMES[colorOrKey]) return NODE_THEMES[colorOrKey];
    // Check if it's a hex mapped to key
    if (PALETTE_MAP[colorOrKey]) return NODE_THEMES[PALETTE_MAP[colorOrKey]];
    // Default
    return NODE_THEMES.gray;
}

// ═══════════════════════════════════════════════════════════════════
// 3. NodeHeader 组件
// ═══════════════════════════════════════════════════════════════════

/** 带可编辑 nodeId 时的 Header 高度（标题 + 下方 ID） */
export const NODE_HEADER_WITH_ID_HEIGHT = 52;

export interface NodeHeaderProps {
    icon: React.ReactNode;
    title: string;
    theme: NodeTheme | string;
    height?: number;
    extra?: React.ReactNode;
    /** 传入此回调后，标题支持双击编辑 */
    onTitleChange?: (newTitle: string) => void;
    /**
     * 节点 ID（DSL cell.id），展示在标题下方；配合 onNodeIdChange 可双击编辑
     */
    nodeId?: string;
    /** 提交新节点 ID（校验/重写引用由调用方或 renameFlowNodeId 完成） */
    onNodeIdChange?: (newId: string) => void;
    /** 传入后可自动从注册表读取 description，在右上角显示 info */
    node?: Node;
    /** 覆盖注册表中的节点说明 */
    description?: string;
}

export const NodeHeader: React.FC<NodeHeaderProps> = ({
    icon,
    title,
    theme,
    height,
    extra,
    onTitleChange,
    nodeId,
    onNodeIdChange,
    node,
    description: descriptionOverride,
}) => {
    const t: NodeTheme = typeof theme === 'string' ? (NODE_THEMES[theme] || NODE_THEMES.gray) : theme;
    const resolvedHeight = height ?? (nodeId != null ? NODE_HEADER_WITH_ID_HEIGHT : 40);
    const description = node
        ? resolveNodeDescription(node, descriptionOverride)
        : descriptionOverride?.trim() || undefined;

    const [editing, setEditing] = React.useState(false);
    const [draft, setDraft] = React.useState(title);
    const inputRef = React.useRef<HTMLInputElement | null>(null);

    const [editingId, setEditingId] = React.useState(false);
    const [idDraft, setIdDraft] = React.useState(nodeId || '');
    const idInputRef = React.useRef<HTMLInputElement | null>(null);

    React.useEffect(() => { if (!editing) setDraft(title); }, [title, editing]);
    React.useEffect(() => { if (!editingId) setIdDraft(nodeId || ''); }, [nodeId, editingId]);

    const commitEdit = () => {
        const trimmed = draft.trim();
        if (trimmed && trimmed !== title && onTitleChange) {
            onTitleChange(trimmed);
        }
        setEditing(false);
    };

    const startEdit = () => {
        if (!onTitleChange) return;
        setDraft(title);
        setEditing(true);
        setTimeout(() => inputRef.current?.focus(), 0);
    };

    const commitIdEdit = () => {
        const trimmed = idDraft.trim();
        setEditingId(false);
        if (!trimmed || trimmed === nodeId || !onNodeIdChange) {
            setIdDraft(nodeId || '');
            return;
        }
        onNodeIdChange(trimmed);
    };

    const startIdEdit = () => {
        if (!onNodeIdChange || !nodeId) return;
        setIdDraft(nodeId);
        setEditingId(true);
        setTimeout(() => {
            idInputRef.current?.focus();
            idInputRef.current?.select();
        }, 0);
    };

    const titleEl = editing
        ? React.createElement('input', {
            ref: inputRef,
            value: draft,
            onChange: (e: React.ChangeEvent<HTMLInputElement>) => setDraft(e.target.value),
            onBlur: commitEdit,
            onKeyDown: (e: React.KeyboardEvent) => {
                if (e.key === 'Enter') commitEdit();
                if (e.key === 'Escape') { setDraft(title); setEditing(false); }
            },
            onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
            style: {
                fontSize: 12,
                fontWeight: 600,
                border: 'none',
                outline: 'none',
                background: 'transparent',
                color: t.titleColor,
                padding: 0,
                width: '100%',
                fontFamily: 'inherit',
                lineHeight: '16px',
            },
        })
        : React.createElement('span', {
            onDoubleClick: startEdit,
            style: {
                fontSize: 12,
                fontWeight: 600,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap' as const,
                color: t.titleColor,
                cursor: onTitleChange ? 'text' : 'default',
                lineHeight: '16px',
            },
        }, title);

    const idEl = nodeId != null
        ? (editingId
            ? React.createElement('input', {
                ref: idInputRef,
                value: idDraft,
                onChange: (e: React.ChangeEvent<HTMLInputElement>) => setIdDraft(e.target.value),
                onBlur: commitIdEdit,
                onKeyDown: (e: React.KeyboardEvent) => {
                    if (e.key === 'Enter') commitIdEdit();
                    if (e.key === 'Escape') { setIdDraft(nodeId); setEditingId(false); }
                },
                onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
                onClick: (e: React.MouseEvent) => e.stopPropagation(),
                spellCheck: false,
                style: {
                    fontSize: 10,
                    border: 'none',
                    outline: 'none',
                    background: 'rgba(0,0,0,0.04)',
                    borderRadius: 2,
                    color: '#595959',
                    padding: '0 4px',
                    width: '100%',
                    maxWidth: 160,
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    lineHeight: '14px',
                    marginTop: 2,
                },
            })
            : React.createElement('span', {
                title: onNodeIdChange
                    ? `节点 ID（双击编辑）· JsonPath: $.${nodeId}.out`
                    : `JsonPath: $.${nodeId}.out`,
                onDoubleClick: startIdEdit,
                onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
                style: {
                    fontSize: 10,
                    color: '#8c8c8c',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap' as const,
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    lineHeight: '14px',
                    marginTop: 2,
                    cursor: onNodeIdChange ? 'text' : 'default',
                    maxWidth: '100%',
                    display: 'block',
                },
            }, nodeId))
        : null;

    const titleBlock = React.createElement('div', {
        style: {
            flex: 1,
            minWidth: 0,
            display: 'flex',
            flexDirection: 'column' as const,
            justifyContent: 'center',
            overflow: 'hidden',
        },
    }, titleEl, idEl);

    const infoBtn = description
        ? React.createElement(
            NodeInfoPopover,
            {
                description,
                title: title || '节点说明',
                iconColor: t.primary,
            },
            React.createElement(InfoCircleOutlined, { style: { fontSize: 14 } }),
        )
        : null;

    return React.createElement('div', {
        style: {
            height: resolvedHeight,
            background: t.headerBg,
            display: 'flex',
            alignItems: 'center',
            padding: '0 12px',
            // 轻分割，避免厚色条「分区感」
            borderBottom: '1px solid rgba(15, 23, 42, 0.06)',
            pointerEvents: 'auto' as const,
            flexShrink: 0,
            gap: 8,
        },
    },
        React.createElement('div', {
            style: { color: t.primary, marginRight: 0, display: 'flex', alignItems: 'center', flexShrink: 0 },
        }, icon),
        titleBlock,
        extra || null,
        infoBtn,
    );
};

// ═══════════════════════════════════════════════════════════════════
// 4. NodeWrapper — 根容器（自动处理 overflow + Toolbar）
// ═══════════════════════════════════════════════════════════════════

export interface NodeWrapperProps {
    node: Node;
    selected: boolean;
    themeColor?: string;
    outlineCss: React.CSSProperties;
    backgroundColor?: string;
    extraStyle?: React.CSSProperties;
    children: React.ReactNode;
    addon?: React.ReactNode;
}

export const NodeWrapper: React.FC<NodeWrapperProps> = ({
    node, selected, themeColor, outlineCss, backgroundColor, extraStyle, children, addon,
}) => {
    const [hovered, setHovered] = React.useState(false);
    const graph = node.model?.graph as any;
    // 快照回放：__readonlySnapshot；旧逻辑 interacting===false 仍兼容
    const isReadonly =
        !!graph?.__readonlySnapshot || graph?.options?.interacting === false;

    return React.createElement('div', {
        className: isReadonly ? 'yf-snapshot-readonly' : undefined,
        onMouseEnter: () => setHovered(true),
        onMouseLeave: () => setHovered(false),
        style: {
            position: 'relative' as const,
            width: '100%',
            height: '100%',
            overflow: 'visible',
            // 快照模式整卡不接收指针，交给 X6 处理选中；空白处可平移/缩放
            pointerEvents: (isReadonly ? 'none' : 'auto') as const,
        },
    },
        React.createElement(NodeToolbar, { node, selected, themeColor, visible: hovered && selected && !isReadonly }),
        addon,
        React.createElement('div', {
            style: {
                width: '100%',
                height: '100%',
                position: 'relative' as const,
                backgroundColor: backgroundColor || '#ffffff',
                ...outlineCss,
                display: 'flex',
                flexDirection: 'column' as const,
                overflow: 'hidden',
                boxSizing: 'border-box' as const,
                pointerEvents: 'none' as const,
                userSelect: 'none' as const,
                ...extraStyle,
            },
        }, children),
    );
};

/** @deprecated 使用 NodeWrapper 组件替代 */
export function nodeRootStyle(opts: NodeRootStyleOpts): React.CSSProperties {
    return {
        width: '100%', height: '100%', position: 'relative',
        backgroundColor: opts.backgroundColor || '#ffffff',
        ...opts.outlineCss,
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
        boxSizing: 'border-box', pointerEvents: 'none', userSelect: 'none',
        ...opts.extra,
    };
}

export interface NodeRootStyleOpts {
    backgroundColor?: string;
    outlineCss: React.CSSProperties;
    extra?: React.CSSProperties;
}

// ═══════════════════════════════════════════════════════════════════
// 5. Footer / Resize 安全区（出口文案勿与手柄重叠）
// ═══════════════════════════════════════════════════════════════════

/** ResizeHandle 热区边长 */
export const RESIZE_HANDLE_SIZE = 25;
/** Footer 默认高度（含单出口 Result / Output / out） */
export const NODE_FOOTER_HEIGHT = 44;
/**
 * Footer 右侧内边距：必须 ≥ ResizeHandle 热区，避免「out」文案与缩放点重叠。
 * 标签用 paddingRight 留白，不要用 absolute right: 10。
 */
export const NODE_FOOTER_SAFE_RIGHT = RESIZE_HANDLE_SIZE + 6; // 31
/** 单出口端口相对 Footer 顶边的 Y 偏移（与标签垂直居中对齐） */
export const NODE_FOOTER_PORT_OFFSET_Y = 22;

export type NodeOutFooterProps = {
    /** 右侧出口文案，如 Result / Output / out */
    label?: string;
    /** 文案颜色，默认 #595959 */
    color?: string;
    height?: number;
    /** 额外右侧内容（在文案左侧） */
    children?: React.ReactNode;
    style?: React.CSSProperties;
    /** 顶部分割线颜色 */
    borderColor?: string;
};

/**
 * 单出口 Footer：右对齐标签 + 为 ResizeHandle 预留安全区。
 * 端口 Y = nodeHeight - height + NODE_FOOTER_PORT_OFFSET_Y
 * 注意：本文件为 .ts，禁止 JSX，统一用 createElement。
 */
export const NodeOutFooter: React.FC<NodeOutFooterProps> = ({
    label = 'Output',
    color = '#595959',
    height = NODE_FOOTER_HEIGHT,
    children,
    style,
    borderColor = '#f0f0f0',
}) => React.createElement(
    'div',
    {
        style: {
            height,
            position: 'relative' as const,
            pointerEvents: 'auto' as const,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 8,
            paddingLeft: 12,
            paddingRight: NODE_FOOTER_SAFE_RIGHT,
            borderTop: `1px solid ${borderColor}`,
            boxSizing: 'border-box' as const,
            ...style,
        },
    },
    children,
    React.createElement(
        'span',
        { style: { fontSize: 12, color, lineHeight: 1, userSelect: 'none' as const } },
        label,
    ),
);

// ═══════════════════════════════════════════════════════════════════
// 6. ResizeHandle — 右下角缩放手柄（公共组件）
// ═══════════════════════════════════════════════════════════════════

const RESIZE_ICON = React.createElement('svg', { viewBox: '0 0 12 12', width: 20, height: 20, fill: 'currentColor' },
    React.createElement('path', { d: 'M8.5 8.5h1v1h-1zM6 8.5h1v1h-1zM3.5 8.5h1v1h-1zM8.5 6h1v1h-1zM6 6h1v1h-1zM8.5 3.5h1v1h-1z' }),
);

export interface ResizeHandleProps {
    node: Node;
    minWidth: number;
    minHeight: number;
    /**
     * both：宽高可调（含 CodeEditor / TextArea 的节点）
     * x：仅左右拉宽（无大文本编辑区的节点）
     */
    axes?: 'both' | 'x';
    /** 缩放过程中的回调（用于实时更新端口位置等） */
    onResize?: (width: number, height: number) => void;
    /** 缩放开始 */
    onResizeStart?: () => void;
    /** 缩放结束 */
    onResizeEnd?: () => void;
    /** 图标颜色 */
    color?: string;
}

export const ResizeHandle: React.FC<ResizeHandleProps> = ({
    node,
    minWidth,
    minHeight,
    axes = 'both',
    onResize,
    onResizeStart,
    onResizeEnd,
    color = '#1677ff',
}) => {
    const handleMouseDown = React.useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();
        onResizeStart?.();

        const sx = e.clientX, sy = e.clientY, ss = node.getSize();
        const onMove = (ev: MouseEvent) => {
            const nw = Math.max(minWidth, ss.width + ev.clientX - sx);
            const nh = axes === 'x'
                ? ss.height
                : Math.max(minHeight, ss.height + ev.clientY - sy);
            node.resize(nw, nh);
            onResize?.(nw, nh);
        };
        const onUp = () => {
            onResizeEnd?.();
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };
        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    }, [node, minWidth, minHeight, axes, onResize, onResizeStart, onResizeEnd]);

    return React.createElement('div', {
        className: 'yf-resize-handle',
        onMouseDown: handleMouseDown,
        style: {
            position: 'absolute' as const,
            bottom: 2,
            right: 2,
            width: RESIZE_HANDLE_SIZE,
            height: RESIZE_HANDLE_SIZE,
            cursor: axes === 'x' ? 'ew-resize' : 'nwse-resize',
            pointerEvents: 'auto' as const,
            zIndex: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color,
        },
    }, RESIZE_ICON);
};

// ═══════════════════════════════════════════════════════════════════
// 7. NodeToolbar — 顶部悬浮工具栏 (Refactored)
// ═══════════════════════════════════════════════════════════════════

// 已弃用自定义 SVG，全部替换为 @ant-design/icons 组件

// 色板预设
const COLOR_PALETTE = [
    '#1f1f1f', '#1677ff', '#52c41a', '#fa8c16',
    '#f5222d', '#722ed1', '#eb2f96', '#13c2c2',
];

export interface NodeToolbarProps {
    node: Node;
    selected: boolean;
    /** 主题色 */
    themeColor?: string;
    /** 是否可见（由父组件控制 hover） */
    visible?: boolean;
}

export const NodeToolbar: React.FC<NodeToolbarProps> = ({ node, selected, themeColor, visible = false }) => {
    const [showPalette, setShowPalette] = React.useState(false);
    const paletteRef = React.useRef<HTMLDivElement>(null);

    // Click outside to close palette
    React.useEffect(() => {
        if (!showPalette) return;
        const handleDocClick = () => setShowPalette(false);
        document.addEventListener('click', handleDocClick);
        return () => document.removeEventListener('click', handleDocClick);
    }, [showPalette]);

    if (!selected) return null;

    const data = node.getData();
    const currentDataColor = data?.themeColor;
    const isDefault = !currentDataColor;

    const btnStyle: React.CSSProperties = {
        width: 32, height: 32, display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer', color: '#595959', borderRadius: 6,
        transition: 'background-color 0.15s, color 0.15s',
    };

    const handleCopy = (e: React.MouseEvent) => {
        e.stopPropagation();
        const graph = node.model?.graph;
        if (!graph) return;
        const pos = node.getPosition();
        const cloned = node.clone();
        cloned.setPosition(pos.x + 40, pos.y + 40);
        cloned.setData({ ...node.getData() });
        graph.addNode(cloned);
    };

    const handleDelete = (e: React.MouseEvent) => {
        e.stopPropagation();
        const graph = node.model?.graph;
        if (!graph) return;
        graph.removeNode(node);
    };

    const handleToggleBreakpoint = (e: React.MouseEvent) => {
        e.stopPropagation();
        const graph = node.model?.graph;
        if (!graph) return;
        graph.trigger('node:toggle-breakpoint', { node });
    };

    const handleColorChange = (color?: string) => {
        // color undefined means reset
        const data = { ...node.getData() };
        if (color) {
            data.themeColor = color;
        } else {
            delete data.themeColor;
        }
        // 使用 overwrite: true 确保 themeColor 字段被移除（默认 merge 不会删除缺失字段）
        node.setData(data, { overwrite: true });
        setShowPalette(false);
    };

    // Keep visible if external control says so OR if palette is open
    const isVisible = visible || showPalette;

    return React.createElement('div', {
        style: {
            position: 'absolute' as const,
            top: -52,
            left: 0,
            zIndex: 100,
            padding: '0 0 12px 0',
            opacity: isVisible ? 1 : 0,
            pointerEvents: isVisible ? 'auto' : 'none',
            transition: 'opacity 0.2s',
        },
    },
        React.createElement('div', {
            style: {
                display: 'flex', alignItems: 'center', gap: 2, padding: '4px 8px',
                background: '#ffffff', borderRadius: 20,
                boxShadow: '0 2px 12px rgba(0,0,0,0.12)', border: '1px solid #e8e8e8',
            },
            onClick: (e: React.MouseEvent) => e.stopPropagation(), // Prevent toolbar clicks from closing palette
        },
            // 1. Color Button
            React.createElement('div', {
                onClick: (e: React.MouseEvent) => { e.stopPropagation(); setShowPalette(!showPalette); },
                style: { ...btnStyle, position: 'relative' as const },
                title: '修改颜色',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            },
                React.createElement('div', {
                    style: {
                        width: 16, height: 16, borderRadius: '50%',
                        backgroundColor: isDefault ? (themeColor || '#595959') : currentDataColor,
                        border: '2px solid #ffffff', boxShadow: '0 0 0 1px #d9d9d9',
                    },
                }),
                // Palette
                showPalette && React.createElement('div', {
                    ref: paletteRef,
                    onClick: (e: React.MouseEvent) => e.stopPropagation(),
                    style: {
                        position: 'absolute' as const,
                        left: -4,
                        bottom: '100%',
                        marginBottom: 12,
                        padding: 8,
                        background: '#ffffff', borderRadius: 8,
                        boxShadow: '0 3px 6px -4px rgba(0,0,0,0.12), 0 6px 16px 0 rgba(0,0,0,0.08), 0 9px 28px 8px rgba(0,0,0,0.05)',
                        display: 'flex', gap: 8, flexWrap: 'nowrap', // Row layout
                        border: '1px solid #f0f0f0', cursor: 'default',
                    },
                },
                    // Reset Button (Gray square with black dot)
                    React.createElement('div', {
                        onClick: (e: React.MouseEvent) => { e.stopPropagation(); handleColorChange(undefined); },
                        title: '恢复默认',
                        style: {
                            width: 20, height: 20, borderRadius: 4, cursor: 'pointer',
                            backgroundColor: '#f5f5f5', border: '1px solid #d9d9d9',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            flexShrink: 0,
                            transform: isDefault ? 'scale(1.1)' : 'scale(1)',
                            boxShadow: isDefault ? '0 0 0 2px #1677ff' : 'none',
                        },
                    }, React.createElement('div', { style: { width: 6, height: 6, borderRadius: '50%', backgroundColor: '#000000' } })),

                    // Colors
                    COLOR_PALETTE.map(c => React.createElement('div', {
                        key: c,
                        onClick: (e: React.MouseEvent) => { e.stopPropagation(); handleColorChange(c); },
                        style: {
                            width: 20, height: 20, borderRadius: 4, cursor: 'pointer',
                            backgroundColor: c,
                            border: c === '#ffffff' ? '1px solid #d9d9d9' : 'none',
                            transform: (!isDefault && c === currentDataColor) ? 'scale(1.1)' : 'scale(1)',
                            boxShadow: (!isDefault && c === currentDataColor) ? '0 0 0 2px #1677ff' : 'none',
                            flexShrink: 0,
                        },
                    }))
                )
            ),
            React.createElement('div', { style: { width: 1, height: 20, background: '#e8e8e8', margin: '0 4px' } }),
            React.createElement('div', {
                onClick: handleToggleBreakpoint,
                style: { ...btnStyle, color: '#eb2f96' },
                title: '切换断点 (Breakpoint)',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#fff0f6'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, React.createElement(BugOutlined)),
            React.createElement('div', {
                style: btnStyle, title: '设置',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, React.createElement(SettingOutlined)),
            (() => {
                const desc = resolveNodeDescription(node);
                const infoIcon = React.createElement('div', {
                    style: btnStyle,
                    title: desc ? '节点说明' : '暂无说明',
                    onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                    onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
                }, React.createElement(InfoCircleOutlined));
                if (!desc) return infoIcon;
                const type = (node.getData() as any)?.__dslType as DslNodeType | undefined;
                const label = type ? getNodeRegistration(type)?.label : undefined;
                return React.createElement(
                    NodeInfoPopover,
                    { description: desc, title: label || '节点说明' },
                    infoIcon,
                );
            })(),
            React.createElement('div', {
                onClick: handleCopy,
                style: btnStyle, title: '复制',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, React.createElement(CopyOutlined)),
            React.createElement('div', {
                onClick: handleDelete,
                style: { ...btnStyle, color: '#ff4d4f' },
                title: '删除',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#fff1f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, React.createElement(DeleteOutlined)),
        )
    );
};

// ============================================================================
// useNodeSelection.ts
// 公共 Hook & 组件: 节点卡片选中态 + 统一 Header + 根容器样式 + ResizeHandle + Toolbar
// 所有 React Shape 节点共用
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';

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

export function useNodeSelection(node: Node, opts: NodeSelectionOpts = {}): NodeSelectionResult {
    const {
        defaultColor = '#d9d9d9',
        selectedColor = '#1677ff',
        defaultWidth = 1,
        selectedWidth = 3,
        borderRadius = 12,
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
    const borderWidth = selected ? selectedWidth : defaultWidth;

    return {
        selected,
        borderColor,
        borderWidth,
        attrs,
        outlineStyle: 'solid',
        // Use standard border (content box will shrink, but headers won't cover it)
        outlineCss: {
            border: `${borderWidth}px solid ${borderColor}`,
            borderRadius,
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
    // New themes for palette
    magenta: {
        primary: '#eb2f96', headerBg: '#fff0f6', headerBorder: '#ffadd2', titleColor: '#c41d7f', bodyBg: '#ffffff',
    },
    cyan: {
        primary: '#13c2c2', headerBg: '#e6fffb', headerBorder: '#87e8de', titleColor: '#08979c', bodyBg: '#ffffff',
    },
    dark: { // #1f1f1f
        primary: '#1f1f1f', headerBg: '#f5f5f5', headerBorder: '#d9d9d9', titleColor: '#000000', bodyBg: '#ffffff',
    }
};

export const PALETTE_MAP: Record<string, string> = {
    '#1f1f1f': 'dark', '#1677ff': 'blue', '#52c41a': 'green', '#fa8c16': 'orange',
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

export interface NodeHeaderProps {
    icon: React.ReactNode;
    title: string;
    theme: NodeTheme | string;
    height?: number;
    extra?: React.ReactNode;
    /** 传入此回调后，标题支持双击编辑 */
    onTitleChange?: (newTitle: string) => void;
}

export const NodeHeader: React.FC<NodeHeaderProps> = ({ icon, title, theme, height = 40, extra, onTitleChange }) => {
    const t: NodeTheme = typeof theme === 'string' ? (NODE_THEMES[theme] || NODE_THEMES.gray) : theme;
    const [editing, setEditing] = React.useState(false);
    const [draft, setDraft] = React.useState(title);
    const inputRef = React.useRef<HTMLInputElement | null>(null);

    // 同步外部 title 变化
    React.useEffect(() => { if (!editing) setDraft(title); }, [title, editing]);

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
        // 下一帧聚焦
        setTimeout(() => inputRef.current?.focus(), 0);
    };

    // 标题元素：可编辑 or 静态
    const titleEl = editing
        ? React.createElement('input', {
            ref: inputRef,
            value: draft,
            onChange: (e: React.ChangeEvent<HTMLInputElement>) => setDraft(e.target.value),
            onBlur: commitEdit,
            onKeyDown: (e: React.KeyboardEvent) => { if (e.key === 'Enter') commitEdit(); if (e.key === 'Escape') { setDraft(title); setEditing(false); } },
            onMouseDown: (e: React.MouseEvent) => e.stopPropagation(),
            style: {
                fontSize: 12,
                fontWeight: 600,
                flex: 1,
                border: 'none',
                outline: 'none',
                background: 'transparent',
                color: t.titleColor,
                padding: 0,
                width: '100%',
                fontFamily: 'inherit',
            },
        })
        : React.createElement('span', {
            onDoubleClick: startEdit,
            style: {
                fontSize: 12,
                fontWeight: 600,
                flex: 1,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap' as const,
                color: t.titleColor,
                cursor: onTitleChange ? 'text' : 'default',
            },
        }, title);

    return React.createElement('div', {
        style: {
            height,
            background: t.headerBg,
            display: 'flex',
            alignItems: 'center',
            padding: '0 12px',
            borderBottom: `1px solid ${t.headerBorder}`,
            pointerEvents: 'auto' as const,
            flexShrink: 0,
        },
    },
        React.createElement('div', {
            style: { color: t.primary, marginRight: 8, display: 'flex', alignItems: 'center' },
        }, icon),
        titleEl,
        extra || null,
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

    return React.createElement('div', {
        onMouseEnter: () => setHovered(true),
        onMouseLeave: () => setHovered(false),
        style: {
            position: 'relative' as const,
            width: '100%',
            height: '100%',
            overflow: 'visible',
            pointerEvents: 'auto' as const, // 关键：允许捕获 hover 事件
        },
    },
        React.createElement(NodeToolbar, { node, selected, themeColor, visible: hovered && selected }),
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
// 5. ResizeHandle — 右下角缩放手柄（公共组件）
// ═══════════════════════════════════════════════════════════════════

const RESIZE_ICON = React.createElement('svg', { viewBox: '0 0 12 12', width: 20, height: 20, fill: 'currentColor' },
    React.createElement('path', { d: 'M8.5 8.5h1v1h-1zM6 8.5h1v1h-1zM3.5 8.5h1v1h-1zM8.5 6h1v1h-1zM6 6h1v1h-1zM8.5 3.5h1v1h-1z' }),
);

export interface ResizeHandleProps {
    node: Node;
    minWidth: number;
    minHeight: number;
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
    node, minWidth, minHeight, onResize, onResizeStart, onResizeEnd, color = '#1677ff',
}) => {
    const handleMouseDown = React.useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();
        onResizeStart?.();

        const sx = e.clientX, sy = e.clientY, ss = node.getSize();
        const onMove = (ev: MouseEvent) => {
            const nw = Math.max(minWidth, ss.width + ev.clientX - sx);
            const nh = Math.max(minHeight, ss.height + ev.clientY - sy);
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
    }, [node, minWidth, minHeight, onResize, onResizeStart, onResizeEnd]);

    return React.createElement('div', {
        onMouseDown: handleMouseDown,
        style: {
            position: 'absolute' as const,
            bottom: 2,
            right: 2,
            width: 25,
            height: 25,
            cursor: 'nwse-resize',
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
// 6. NodeToolbar — 顶部悬浮工具栏 (Refactored)
// ═══════════════════════════════════════════════════════════════════

const TOOLBAR_ICONS = {
    settings: React.createElement('svg', { viewBox: '0 0 1024 1024', width: 16, height: 16, fill: 'currentColor' },
        React.createElement('path', { d: 'M924.8 625.7l-65.5-56c3.1-19 4.7-38.4 4.7-57.8s-1.6-38.8-4.7-57.8l65.5-56a32.03 32.03 0 009.3-35.2l-.9-2.6a443.74 443.74 0 00-79.7-137.9l-1.8-2.1a32.12 32.12 0 00-35.1-9.5l-81.3 28.9c-30-24.6-63.5-44-99.7-57.6l-15.7-85a32.05 32.05 0 00-25.8-25.7l-2.7-.5c-52.1-9.4-106.9-9.4-159 0l-2.7.5a32.05 32.05 0 00-25.8 25.7l-15.8 85.4a351.86 351.86 0 00-99 57.4l-81.9-29.1a32 32 0 00-35.1 9.5l-1.8 2.1a446.02 446.02 0 00-79.7 137.9l-.9 2.6c-4.5 12.5-1.5 26.5 9.3 35.2l66.3 56.6c-3.1 18.8-4.6 38-4.6 57.1 0 19.2 1.5 38.4 4.6 57.1L99 625.5a32.03 32.03 0 00-9.3 35.2l.9 2.6c18.1 50.4 44.9 96.9 79.7 137.9l1.8 2.1a32.12 32.12 0 0035.1 9.5l81.9-29.1c29.8 24.5 63.1 43.9 99 57.4l15.8 85.4a32.05 32.05 0 0025.8 25.7l2.7.5a449.4 449.4 0 00159 0l2.7-.5a32.05 32.05 0 0025.8-25.7l15.7-85a350 350 0 0099.7-57.6l81.3 28.9a32 32 0 0035.1-9.5l1.8-2.1c34.8-41.1 61.6-87.5 79.7-137.9l.9-2.6c4.3-12.3 1.4-26.2-9.1-35zM788.3 465.9c2.5 15.1 3.8 30.6 3.8 46.1s-1.3 31-3.8 46.1l-6.6 40.1 74.7 63.9A372.95 372.95 0 00877 608l.3-.6-64.4 75.2c-5.8 6.8-11.1 12.9-15.3 17.9l-30.9-10.9-10.8-3.8-76.3-27.4c-11.7 12.3-24.3 23.4-37.5 33.3l-5.6 4.3l-14.8 79.8-7.9 42.6c-21.9 3.9-43.9 3.9-65.8 0l-7.9-42.6-14.8-79.8-5.6-4.3c-13.2-9.9-25.8-20.9-37.5-33.3l-76.3 27.4-10.8 3.8-30.9 10.9c-4.2-5-9.5-11.1-15.3-17.9L247 607.3l.3.6-20.6-54.1 74.7-63.9-6.6-40.1c-2.5-15.1-3.8-30.6-3.8-46.1s1.3-31 3.8-46.1l6.6-40.1-74.7-63.9 20.6-54.1-.3.6 64.4-75.2c5.8-6.8 11.1-12.9 15.3-17.9l30.9 10.9 10.8 3.8 76.3 27.4c11.7-12.3 24.3-23.4 37.5-33.3l5.6-4.3 14.8-79.8 7.9-42.6c21.9-3.9 43.9-3.9 65.8 0l7.9 42.6 14.8 79.8 5.6 4.3c13.2 9.9 25.8 20.9 37.5 33.3l76.3-27.4 10.8-3.8 30.9-10.9c4.2 5 9.5 11.1 15.3 17.9l64.4 75.2-.3-.6 20.6 54.1-74.7 63.9 6.6 40.1zM512 394c-65.1 0-118 52.9-118 118s52.9 118 118 118s118-52.9 118-118s-52.9-118-118-118zm0 168c-27.6 0-50-22.4-50-50s22.4-50 50-50 50 22.4 50 50-22.4 50-50 50z' }),
    ),
    info: React.createElement('svg', { viewBox: '0 0 1024 1024', width: 16, height: 16, fill: 'currentColor' },
        React.createElement('path', { d: 'M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z' }),
        React.createElement('path', { d: 'M464 336a48 48 0 1096 0 48 48 0 10-96 0zm72 112h-48c-4.4 0-8 3.6-8 8v272c0 4.4 3.6 8 8 8h48c4.4 0 8-3.6 8-8V456c0-4.4-3.6-8-8-8z' }),
    ),
    copy: React.createElement('svg', { viewBox: '0 0 1024 1024', width: 16, height: 16, fill: 'currentColor' },
        React.createElement('path', { d: 'M832 64H296c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8h496v688c0 4.4 3.6 8 8 8h56c4.4 0 8-3.6 8-8V96c0-17.7-14.3-32-32-32zM704 192H192c-17.7 0-32 14.3-32 32v530.7c0 8.5 3.4 16.6 9.4 22.6l173.3 173.3c2.2 2.2 4.7 4 7.4 5.5v1.9h4.2c3.5 1.3 7.2 2 11 2H704c17.7 0 32-14.3 32-32V224c0-17.7-14.3-32-32-32zM350 856.2L263.9 770H350v86.2zM664 888H414V746c0-22.1-17.9-40-40-40H232V264h432v624z' }),
    ),
    delete: React.createElement('svg', { viewBox: '0 0 1024 1024', width: 16, height: 16, fill: 'currentColor' },
        React.createElement('path', { d: 'M360 184h-8c4.4 0 8-3.6 8-8v8h304v-8c0 4.4 3.6 8 8 8h-8v72h72v-80c0-35.3-28.7-64-64-64H352c-35.3 0-64 28.7-64 64v80h72v-72zm504 72H160c-17.7 0-32 14.3-32 32v32c0 4.4 3.6 8 8 8h60.4l24.7 523c1.6 34.1 29.8 61 63.9 61h454c34.2 0 62.3-26.8 63.9-61l24.7-523H888c4.4 0 8-3.6 8-8v-32c0-17.7-14.3-32-32-32zM731.3 840H292.7l-24.2-512h487l-24.2 512z' }),
    ),
};

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
                style: btnStyle, title: '设置',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, TOOLBAR_ICONS.settings),
            React.createElement('div', {
                style: btnStyle, title: '详情',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, TOOLBAR_ICONS.info),
            React.createElement('div', {
                onClick: handleCopy,
                style: btnStyle, title: '复制',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, TOOLBAR_ICONS.copy),
            React.createElement('div', {
                onClick: handleDelete,
                style: { ...btnStyle, color: '#ff4d4f' },
                title: '删除',
                onMouseEnter: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = '#fff1f0'; },
                onMouseLeave: (e: React.MouseEvent) => { (e.currentTarget as HTMLElement).style.backgroundColor = ''; },
            }, TOOLBAR_ICONS.delete),
        )
    );
};

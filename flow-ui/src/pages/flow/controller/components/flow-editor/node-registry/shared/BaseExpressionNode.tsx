// ============================================================================
// BaseExpressionNode.tsx — 表达式类节点通用基座组件
// 抽取 Evaluate / If 节点中的公共 UI: Header + 语言下拉 + 动态变量列表 + CodeEditor
// 底部端口区域通过 bottomContent prop 由消费方自定义
// ============================================================================

import React from 'react';
import { Typography, Dropdown } from 'antd';
import CodeEditor from '../../components/CodeEditor';
import { Node } from '@antv/x6';
import { useNodeSelection, NodeHeader, NodeWrapper, ResizeHandle, getNodeTheme } from './useNodeSelection';
import { useNodeVariables } from './useNodeVariables';
import { DynamicVariableList } from './DynamicVariableList';

const { Text } = Typography;

const ICONS = {
    chevron: (<svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor"><path d="M831.872 340.864 512 652.672 192.128 340.864a30.592 30.592 0 0 0-42.752 0 29.12 29.12 0 0 0 0 41.6L489.664 714.24a32 32 0 0 0 44.672 0l340.288-331.712a29.12 29.12 0 0 0 0-41.728 30.592 30.592 0 0 0-42.752 0z" /></svg>),
};

// ── 布局常量 (导出供消费方复用) ──
export const ROW_HEIGHT = 40;
export const HEADER_HEIGHT = 40;
export const MIN_QUERY_HEIGHT = 60;
export const MIN_WIDTH = 280;
export const VAR_PADDING = 8;
export const COND_PADDING = 16;

export const varPortY = (idx: number) => HEADER_HEIGHT + VAR_PADDING / 2 + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

// ── 语言菜单项 ──
const LANGUAGE_ITEMS = [
    { key: 'JavaScript', label: 'JavaScript' },
    { key: 'Aviator', label: 'Aviator' },
    { key: 'SpEL', label: 'SpEL' },
];

// ── 底部内容渲染函数的参数 ──
export interface BottomContentProps {
    /** 当前节点尺寸 */
    size: { width: number; height: number };
}

export interface BaseExpressionNodeProps {
    /** X6 节点实例 */
    node: Node;
    /** 标题前的图标 */
    titleIcon: React.ReactNode;
    /** 默认标题文字 (如 "Evaluate" 或 "If") */
    titleText: string;
    /** 底部区域高度 (Evaluate = 44, If = 90) */
    footerHeight: number;
    /** 表达式字段名 (Evaluate 用 "expression", If 用 "condition") */
    expressionField?: string;
    /** 底部个性化端口区 */
    bottomContent: React.ReactNode | ((props: BottomContentProps) => React.ReactNode);
    /**
     * 缩放时的端口位置同步回调
     * 由消费方根据自身端口布局定义
     */
    onResize?: (node: Node, newWidth: number, newHeight: number, updateEdges: (portId: string) => void) => void;
    /**
     * 端口初始化同步回调 (variables / size 变化时)
     * 由消费方管理自身特有的固定端口
     */
    onPortSync?: (node: Node, size: { width: number; height: number }, variables: any[]) => void;
    /**
     * 非缩放时的底部端口位置同步回调
     */
    onPortPositionSync?: (node: Node, size: { width: number; height: number }, updateEdges: (portId: string) => void) => void;
}

// ============================================================================
export const BaseExpressionNode: React.FC<BaseExpressionNodeProps> = ({
    node, titleIcon, titleText, footerHeight, expressionField = 'expression',
    bottomContent, onResize, onPortSync, onPortPositionSync,
}) => {
    const [data, setData] = React.useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor);
    const { outlineCss, borderColor, selected } = useNodeSelection(node, { defaultColor: themeObj.primary, selectedColor: themeObj.primary });
    const [size, setSize] = React.useState(node.getSize());

    React.useEffect(() => {
        const onD = () => setData({ ...node.getData() });
        const onS = () => setSize({ ...node.getSize() });
        node.on('change:data', onD);
        node.on('change:size', onS);
        return () => { node.off('change:data', onD); node.off('change:size', onS); };
    }, [node]);

    const expression = data?.[expressionField] || '';
    const language = data?.language || 'JavaScript';

    // ── 使用共享变量 Hook ──
    const {
        variables,
        dragState,
        hoverRowIndex,
        setHoverRowIndex,
        updateEdges,
        onAddVar,
        onUpdateVar,
        onRemoveVar,
        handleDragStart,
    } = useNodeVariables(node, { varPortY, rowHeight: ROW_HEIGHT });

    // ── 端口同步 (委托给消费方) ──
    React.useEffect(() => {
        onPortSync?.(node, node.getSize(), variables);
    }, [variables, node, size]);

    // ── 缩放 ──
    const [resizing, setResizing] = React.useState(false);
    const contentH = HEADER_HEIGHT + variables.length * ROW_HEIGHT + VAR_PADDING + COND_PADDING + footerHeight;
    const minH = contentH + MIN_QUERY_HEIGHT;

    React.useEffect(() => {
        if (!resizing) {
            const s = node.getSize();
            if (s.height < minH) node.resize(Math.max(s.width, MIN_WIDTH), minH);
        }
    }, [minH, node, resizing]);

    const handleResize = React.useCallback((nw: number, nh: number) => {
        onResize?.(node, nw, nh, updateEdges);
    }, [node, updateEdges, onResize]);

    // ── 底部端口位置同步 (非缩放时) ──
    React.useEffect(() => {
        if (resizing) return;
        onPortPositionSync?.(node, node.getSize(), updateEdges);
    }, [size, variables.length, resizing]);

    // ── 标题 ──
    const nodeLabel = (data as any)?.__label || titleText;
    const handleTitleChange = React.useCallback((newTitle: string) => {
        node.setData({ ...node.getData(), __label: newTitle }, { overwrite: true });
    }, [node]);

    const langMenu = {
        items: LANGUAGE_ITEMS,
        onClick: ({ key }: any) => node.setData({ ...node.getData(), language: key }, { overwrite: true }),
    };

    /** 将节点 language 映射为 CodeEditor 的 language prop */
    const editorLanguage = (language === 'JavaScript' || language === 'js') ? 'javascript' : 'text';

    return (
        <NodeWrapper node={node} selected={selected} themeColor={borderColor} outlineCss={outlineCss} backgroundColor={themeObj.bodyBg}>
            {/* Header */}
            <NodeHeader icon={titleIcon} title={nodeLabel} theme={themeObj} height={HEADER_HEIGHT}
                onTitleChange={handleTitleChange}
                extra={
                    <Dropdown menu={langMenu} trigger={['click']}>
                        <div onClick={(e) => e.stopPropagation()} onMouseDown={(e) => e.stopPropagation()} style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                            <Text style={{ fontSize: 11, color: themeObj.primary }}>{language}</Text>
                            <div style={{ color: themeObj.primary, display: 'flex' }}>{ICONS.chevron}</div>
                        </div>
                    </Dropdown>
                }
            />

            {/* Variables — 使用共享组件 */}
            <DynamicVariableList
                variables={variables}
                rowHeight={ROW_HEIGHT}
                dragState={dragState}
                hoverRowIndex={hoverRowIndex}
                onHoverChange={setHoverRowIndex}
                onDragStart={handleDragStart}
                onAddVar={onAddVar}
                onUpdateVar={onUpdateVar}
                onRemoveVar={onRemoveVar}
            />

            {/* Expression Editor — CodeMirror 代码编辑器 */}
            <div
                style={{ padding: '8px 12px', pointerEvents: 'auto', flex: 1, display: 'flex', flexDirection: 'column', minHeight: MIN_QUERY_HEIGHT }}
                onMouseDown={(e) => e.stopPropagation()}
                onMouseUp={(e) => e.stopPropagation()}
                onPointerDown={(e) => e.stopPropagation()}
                onClick={(e) => e.stopPropagation()}
            >
                <CodeEditor
                    value={expression}
                    onChange={(val) => node.setData({ ...node.getData(), [expressionField]: val }, { overwrite: true })}
                    language={editorLanguage}
                    height="100%"
                    maxHeight="250px"
                    fontSize={12}
                    lineNumbers={false}
                    theme="light"
                    style={{ flex: 1, minHeight: MIN_QUERY_HEIGHT }}
                />
            </div>

            {/* Footer — 由消费方定义 */}
            {typeof bottomContent === 'function'
                ? bottomContent({ size })
                : bottomContent
            }

            {/* Resize */}
            <ResizeHandle node={node} minWidth={MIN_WIDTH} minHeight={minH} onResize={handleResize} color={themeObj.primary}
                onResizeStart={() => setResizing(true)} onResizeEnd={() => setResizing(false)} />
        </NodeWrapper>
    );
};

export default BaseExpressionNode;

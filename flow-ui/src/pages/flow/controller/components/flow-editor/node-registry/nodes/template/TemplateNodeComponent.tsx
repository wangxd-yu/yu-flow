// ============================================================================
// TemplateNodeComponent.tsx — Template 节点（对齐 Postman / Evaluate 布局）
// · 左上角 in:payload 总入口
// · 变量行 + in:var 连线（与 Evaluate 相同）
// · 下方模板正文 {{key}}；Footer Output + Resize 安全区
// ============================================================================

import React from 'react';
import { Node } from '@antv/x6';
import {
    BaseExpressionNode,
    HEADER_HEIGHT, ROW_HEIGHT, VAR_PADDING, COND_PADDING, MIN_WIDTH, MIN_QUERY_HEIGHT,
} from '../../shared/BaseExpressionNode';
import {
    NodeOutFooter,
    NODE_FOOTER_HEIGHT,
    NODE_FOOTER_PORT_OFFSET_Y,
} from '../../shared/useNodeSelection';

import {
    COMPACT_FOOTER_HEIGHT,
    getGraphNodeViewMode,
} from '../../shared/NodeViewMode';

const ICON = (
    <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
        <path d="M192 192h640v96H192zm0 192h384v96H192zm0 192h512v96H192zm0 192h256v96H192z" />
    </svg>
);

export const TEMPLATE_COLOR = '#9254de';

export const TEMPLATE_LAYOUT = {
    headerHeight: HEADER_HEIGHT,
    footerHeight: NODE_FOOTER_HEIGHT,
    width: MIN_WIDTH,
    get totalHeight() {
        return HEADER_HEIGHT + ROW_HEIGHT + VAR_PADDING + MIN_QUERY_HEIGHT + COND_PADDING + NODE_FOOTER_HEIGHT;
    },
    get footerTop() {
        return this.totalHeight - NODE_FOOTER_HEIGHT;
    },
    get outPortY() {
        return this.footerTop + NODE_FOOTER_PORT_OFFSET_Y;
    },
};

function activeFooterHeight(node: Node) {
    return getGraphNodeViewMode(node) === 'compact' ? COMPACT_FOOTER_HEIGHT : NODE_FOOTER_HEIGHT;
}

function outPortY(node: Node, height: number) {
    const fh = activeFooterHeight(node);
    return height - fh + fh / 2;
}

const handlePortSync = (node: Node, size: { width: number; height: number }) => {
    const ports = node.getPorts();
    const existing = new Set(ports.map((p) => p.id));
    const outY = outPortY(node, size.height);
    const outX = size.width;

    if (!existing.has('out')) {
        node.addPort({
            id: 'out',
            group: 'absolute-out-solid',
            args: { x: outX, y: outY, dx: 0 },
            zIndex: 1,
        });
    } else {
        const p = ports.find((port) => port.id === 'out');
        if (p?.attrs?.text?.text !== '') node.setPortProp('out', 'attrs/text/text', '');
        if (p?.group !== 'absolute-out-solid') node.setPortProp('out', 'group', 'absolute-out-solid');
        node.setPortProp('out', 'args', { x: outX, y: outY, dx: 0 });
    }

    // 历史控制流 in 移除；总入口仅用左上角 in:payload
    if (existing.has('in')) node.removePort('in');
};

const handleResize = (node: Node, nw: number, nh: number, updateEdges: (id: string) => void) => {
    node.setPortProp('out', 'args', { x: nw, y: outPortY(node, nh) });
    updateEdges('out');
};

const handlePortPositionSync = (
    node: Node,
    size: { width: number; height: number },
    updateEdges: (id: string) => void,
) => {
    try {
        node.setPortProp('out', 'args', { x: size.width, y: outPortY(node, size.height) });
        updateEdges('out');
    } catch {
        /* ignore */
    }
};

export const TemplateNodeComponent: React.FC<{ node: Node }> = ({ node }) => {
    return (
        <BaseExpressionNode
            node={node}
            titleIcon={ICON}
            titleText="Template"
            footerHeight={NODE_FOOTER_HEIGHT}
            compactFooterHeight={COMPACT_FOOTER_HEIGHT}
            expressionField="template"
            hideLanguage
            forceEditorLanguage="text"
            expressionPlaceholder="Insert variables with {{tags}}"
            onPortSync={handlePortSync}
            onResize={handleResize}
            onPortPositionSync={handlePortPositionSync}
            bottomContent={
                <NodeOutFooter label="Output" />
            }
        />
    );
};

export default TemplateNodeComponent;

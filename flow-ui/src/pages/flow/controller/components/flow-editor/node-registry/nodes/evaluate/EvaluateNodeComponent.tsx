// ============================================================================
// EvaluateNodeComponent.tsx — V4 Evaluate 节点
// 基于 BaseExpressionNode 组合实现，仅定义底部 Result 输出端口区
// ============================================================================

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
    CompactOutFooter,
    compactSingleOutPortY,
    getGraphNodeViewMode,
} from '../../shared/NodeViewMode';

const ICONS = {
    evaluate: (<svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor"><path d="M320 256l192 192-192 192M544 640h192" stroke="currentColor" strokeWidth="72" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>),
};

export const EVALUATE_LAYOUT = {
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

function outPortY(node: Node, height: number) {
    if (getGraphNodeViewMode(node) === 'compact') {
        return compactSingleOutPortY(height);
    }
    return height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
}

const handlePortSync = (node: Node, size: { width: number; height: number }) => {
    const ports = node.getPorts();
    const existing = new Set(ports.map((p) => p.id));
    const outY = outPortY(node, size.height);
    const outX = size.width;

    if (!existing.has('out')) {
        node.addPort({
            id: 'out', group: 'absolute-out-solid',
            args: { x: outX, y: outY, dx: 0 }, zIndex: 1,
        });
    } else {
        const p = ports.find((port) => port.id === 'out');
        if (p?.attrs?.text?.text !== '') node.setPortProp('out', 'attrs/text/text', '');
        if (p?.group !== 'absolute-out-solid') node.setPortProp('out', 'group', 'absolute-out-solid');
        node.setPortProp('out', 'args', { x: outX, y: outY, dx: 0 });
    }

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
    } catch (_) { /* ignore */ }
};

export const EvaluateNodeComponent = ({ node }: { node: Node }) => {
    return (
        <BaseExpressionNode
            node={node}
            titleIcon={ICONS.evaluate}
            titleText="Evaluate"
            footerHeight={NODE_FOOTER_HEIGHT}
            compactFooterHeight={COMPACT_FOOTER_HEIGHT}
            expressionField="expression"
            onPortSync={handlePortSync}
            onResize={handleResize}
            onPortPositionSync={handlePortPositionSync}
            bottomContent={({ isCompact }) =>
                isCompact ? <CompactOutFooter label="Result" /> : <NodeOutFooter label="Result" />
            }
        />
    );
};

export default EvaluateNodeComponent;

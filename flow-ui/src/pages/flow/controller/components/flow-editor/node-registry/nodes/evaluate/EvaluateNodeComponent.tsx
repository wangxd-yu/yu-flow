// ============================================================================
// EvaluateNodeComponent.tsx — V4 Evaluate 节点
// 基于 BaseExpressionNode 组合实现，仅定义底部 Result 输出端口区
// ============================================================================

import { Typography } from 'antd';
import { Node } from '@antv/x6';
import {
    BaseExpressionNode,
    HEADER_HEIGHT, ROW_HEIGHT, VAR_PADDING, COND_PADDING, MIN_WIDTH, MIN_QUERY_HEIGHT,
} from '../../shared/BaseExpressionNode';

const { Text } = Typography;

const ICONS = {
    evaluate: (<svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor"><path d="M320 256l192 192-192 192M544 640h192" stroke="currentColor" strokeWidth="72" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>),
};

// ── 布局常量 ──
const FOOTER_HEIGHT = 44;
const FT_RESULT_Y = 12;

export const EVALUATE_LAYOUT = {
    headerHeight: HEADER_HEIGHT, footerHeight: FOOTER_HEIGHT, width: MIN_WIDTH,
    get totalHeight() { return HEADER_HEIGHT + ROW_HEIGHT + VAR_PADDING + MIN_QUERY_HEIGHT + COND_PADDING + FOOTER_HEIGHT; },
    get footerTop() { return this.totalHeight - FOOTER_HEIGHT; },
    get outPortY() { return this.footerTop + FT_RESULT_Y; },
};

// ── 端口初始化 ──
const handlePortSync = (node: Node, size: { width: number; height: number }, variables: any[]) => {
    const ports = node.getPorts();
    const existing = new Set(ports.map((p) => p.id));

    const ft = size.height - FOOTER_HEIGHT;
    const outY = ft + FT_RESULT_Y;
    const outX = size.width;

    if (!existing.has('out')) {
        node.addPort({
            id: 'out', group: 'absolute-out-solid',
            args: { x: outX, y: outY, dx: 0 }, zIndex: 1,
        });
    } else {
        const p = ports.find(p => p.id === 'out');
        if (p?.attrs?.text?.text !== '') node.setPortProp('out', 'attrs/text/text', '');
        if (p?.group !== 'absolute-out-solid') node.setPortProp('out', 'group', 'absolute-out-solid');
    }

    // Evaluate 节点不需要 'in' 端口
    if (existing.has('in')) node.removePort('in');
};

// ── 缩放时端口位置同步 ──
const handleResize = (node: Node, nw: number, nh: number, updateEdges: (id: string) => void) => {
    const ft = nh - FOOTER_HEIGHT;
    node.setPortProp('out', 'args', { x: nw, y: ft + FT_RESULT_Y });
    updateEdges('out');
};

// ── 非缩放时端口位置同步 ──
const handlePortPositionSync = (node: Node, size: { width: number; height: number }, updateEdges: (id: string) => void) => {
    const ft = size.height - FOOTER_HEIGHT;
    try {
        node.setPortProp('out', 'args', { x: size.width, y: ft + FT_RESULT_Y });
        updateEdges('out');
    } catch (_) { }
};

// ============================================================================
export const EvaluateNodeComponent = ({ node }: { node: Node }) => {
    return (
        <BaseExpressionNode
            node={node}
            titleIcon={ICONS.evaluate}
            titleText="Evaluate"
            footerHeight={FOOTER_HEIGHT}
            expressionField="expression"
            onPortSync={handlePortSync}
            onResize={handleResize}
            onPortPositionSync={handlePortPositionSync}
            bottomContent={
                <div style={{ height: FOOTER_HEIGHT, position: 'relative', pointerEvents: 'auto', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', padding: '0 12px' }}>
                    <div style={{ position: 'absolute', right: 10, top: FT_RESULT_Y, transform: 'translateY(-50%)', display: 'flex', alignItems: 'center', gap: 6 }}>
                        <Text style={{ fontSize: 12, color: '#595959' }}>Result</Text>
                    </div>
                </div>
            }
        />
    );
};

export default EvaluateNodeComponent;

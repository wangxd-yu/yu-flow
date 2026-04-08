// ============================================================================
// IfNodeComponent.tsx — V4 If 节点
// 基于 BaseExpressionNode 组合实现，仅定义底部 THEN / ELSE 分叉端口区
// ============================================================================

import React from 'react';
import { Typography } from 'antd';
import { Node } from '@antv/x6';
import {
    BaseExpressionNode, BottomContentProps,
    HEADER_HEIGHT, ROW_HEIGHT, VAR_PADDING, COND_PADDING, MIN_WIDTH, MIN_QUERY_HEIGHT,
} from '../../shared/BaseExpressionNode';

const { Text } = Typography;

const ICONS = {
    condition: (<svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor"><path d="M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896z m0 72a376 376 0 1 0 0 752 376 376 0 0 0 0-752z m0 480a48 48 0 1 1 0 96 48 48 0 0 1 0-96z m0-336a40 40 0 0 1 40 40v200a40 40 0 0 1-80 0V320a40 40 0 0 1 40-40z" /></svg>),
};

// ── 布局常量 ──
const FOOTER_HEIGHT = 90;
const FT_DATA_Y = 38;
const FT_THEN_Y = 20;
const FT_ELSE_Y = 58;
const CORNER_R = 6;

export const IF_LAYOUT = {
    headerHeight: HEADER_HEIGHT, conditionAreaHeight: MIN_QUERY_HEIGHT, footerHeight: FOOTER_HEIGHT, width: MIN_WIDTH,
    get totalHeight() { return HEADER_HEIGHT + ROW_HEIGHT + VAR_PADDING + MIN_QUERY_HEIGHT + COND_PADDING + FOOTER_HEIGHT; },
    get footerTop() { return this.totalHeight - FOOTER_HEIGHT; },
    get inPortY() { return this.footerTop + FT_DATA_Y; },
    get truePortY() { return this.footerTop + FT_THEN_Y; },
    get falsePortY() { return this.footerTop + FT_ELSE_Y; },
};

// ── 端口初始化 ──
const handlePortSync = (node: Node, size: { width: number; height: number }, _variables: any[]) => {
    const ports = node.getPorts();
    const existing = new Set(ports.map((p) => p.id));
    const ft = size.height - FOOTER_HEIGHT;

    if (!existing.has('in')) {
        node.addPort({ id: 'in', group: 'absolute-in-solid', args: { x: 0, y: ft + FT_DATA_Y }, zIndex: 1 });
    }
    if (!existing.has('true')) {
        node.addPort({ id: 'true', group: 'absolute-out-solid', args: { x: size.width, y: ft + FT_THEN_Y }, zIndex: 1 });
    }
    if (!existing.has('false')) {
        node.addPort({ id: 'false', group: 'absolute-out-solid', args: { x: size.width, y: ft + FT_ELSE_Y }, zIndex: 1 });
    }
};

// ── 缩放时端口位置同步 ──
const handleResize = (node: Node, nw: number, nh: number, updateEdges: (id: string) => void) => {
    const ft = nh - FOOTER_HEIGHT;
    node.setPortProp('in', 'args', { x: 0, y: ft + FT_DATA_Y });
    node.setPortProp('true', 'args', { x: nw, y: ft + FT_THEN_Y });
    node.setPortProp('false', 'args', { x: nw, y: ft + FT_ELSE_Y });
    updateEdges('in'); updateEdges('true'); updateEdges('false');
};

// ── 非缩放时端口位置同步 ──
const handlePortPositionSync = (node: Node, size: { width: number; height: number }, updateEdges: (id: string) => void) => {
    const ft = size.height - FOOTER_HEIGHT;
    try {
        node.setPortProp('in', 'args', { x: 0, y: ft + FT_DATA_Y });
        node.setPortProp('true', 'args', { x: size.width, y: ft + FT_THEN_Y });
        node.setPortProp('false', 'args', { x: size.width, y: ft + FT_ELSE_Y });
        updateEdges('in'); updateEdges('true'); updateEdges('false');
    } catch (_) { }
};

// ── 底部分叉渲染 ──
const IfFooter: React.FC<BottomContentProps> = ({ size }) => {
    const w = size.width;
    const forkX = w / 2 - 10;

    return (
        <div style={{ height: FOOTER_HEIGHT, position: 'relative', pointerEvents: 'auto', flexShrink: 0 }}>
            <svg style={{ position: 'absolute', width: '100%', height: '100%', overflow: 'visible', pointerEvents: 'none' }}>
                <path d={`M 55 ${FT_DATA_Y} H ${forkX}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
                <path d={`M ${forkX} ${FT_DATA_Y} V ${FT_THEN_Y + CORNER_R} Q ${forkX} ${FT_THEN_Y}, ${forkX + CORNER_R} ${FT_THEN_Y} H ${w - 75}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
                <path d={`M ${forkX} ${FT_DATA_Y} V ${FT_ELSE_Y - CORNER_R} Q ${forkX} ${FT_ELSE_Y}, ${forkX + CORNER_R} ${FT_ELSE_Y} H ${w - 75}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
                <path d={`M ${w - 75} ${FT_THEN_Y} H ${w}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
                <path d={`M ${w - 75} ${FT_ELSE_Y} H ${w}`} fill="none" stroke="#d9d9d9" strokeWidth="1" />
            </svg>

            <div style={{ position: 'absolute', left: 12, top: FT_DATA_Y, transform: 'translateY(-50%)' }}><Text style={{ fontSize: 12, color: '#595959' }}>? Data</Text></div>
            <div style={{ position: 'absolute', right: 75, top: FT_THEN_Y, transform: 'translateY(-50%)', background: '#f0f0f0', padding: '2px 8px', borderRadius: 10 }}><Text style={{ fontSize: 10, color: '#8c8c8c' }}>THEN</Text></div>
            <div style={{ position: 'absolute', right: 75, top: FT_ELSE_Y, transform: 'translateY(-50%)', background: '#e6f4ff', padding: '2px 8px', borderRadius: 10 }}><Text style={{ fontSize: 10, color: '#1677ff' }}>ELSE</Text></div>
            <div style={{ position: 'absolute', right: 35, top: FT_THEN_Y, transform: 'translateY(-50%)' }}><Text style={{ fontSize: 12, color: '#bfbfbf' }}>?</Text></div>
            <div style={{ position: 'absolute', right: 35, top: FT_ELSE_Y, transform: 'translateY(-50%)' }}><Text style={{ fontSize: 12, color: '#bfbfbf' }}>?</Text></div>
        </div>
    );
};

// ============================================================================
export const IfNodeComponent = ({ node }: { node: Node }) => {
    return (
        <BaseExpressionNode
            node={node}
            titleIcon={ICONS.condition}
            titleText="If"
            footerHeight={FOOTER_HEIGHT}
            expressionField="condition"
            onPortSync={handlePortSync}
            onResize={handleResize}
            onPortPositionSync={handlePortPositionSync}
            bottomContent={(props: BottomContentProps) => <IfFooter {...props} />}
        />
    );
};

export default IfNodeComponent;

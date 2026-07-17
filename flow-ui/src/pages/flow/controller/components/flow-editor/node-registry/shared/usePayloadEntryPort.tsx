// ============================================================================
// usePayloadEntryPort — Record 风格总入口 in:payload
// 无命名传参时连到左上角入口，写入 inputs.payload 并标明上下游数据流
// ============================================================================

import React, { useEffect } from 'react';
import { Typography } from 'antd';
import type { Node } from '@antv/x6';

const { Text } = Typography;

/** Header 垂直中线（与 Record 一致） */
export const PAYLOAD_PORT_Y = 20; // HEADER_HEIGHT / 2，Header 固定 40

export const PAYLOAD_PORT_ID = 'in:payload';

/** 确保节点存在 absolute-in-solid 的 in:payload 端口 */
export function ensurePayloadPort(node: Node, y: number = PAYLOAD_PORT_Y) {
    const existing = node.getPort(PAYLOAD_PORT_ID);
    if (existing && existing.group !== 'absolute-in-solid') {
        try {
            node.setPortProp(PAYLOAD_PORT_ID, 'group', 'absolute-in-solid');
            node.setPortProp(PAYLOAD_PORT_ID, 'args', { x: 0, y, dx: 0 });
            return;
        } catch {
            node.removePort(PAYLOAD_PORT_ID);
        }
    }
    if (!node.hasPort(PAYLOAD_PORT_ID)) {
        node.addPort({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y, dx: 0 },
            zIndex: 1,
        });
    } else {
        node.setPortProp(PAYLOAD_PORT_ID, 'args', { x: 0, y, dx: 0 });
    }
}

/**
 * 连线到 in:payload 时写入 inputs.payload = { extractPath: '$' }。
 * FlowParser 会在导出时展开为 $.source.out。
 */
export function usePayloadEntryConnection(node: Node) {
    useEffect(() => {
        let disposed = false;
        let graph: any = null;

        const onConnected = ({ edge, ...evtArgs }: any) => {
            const target = edge.getTarget() as any;
            const targetCellId =
                evtArgs?.currentCell?.id
                || edge?.getTargetCellId?.()
                || (typeof target?.cell === 'string' ? target.cell : target?.cell?.id);
            const targetPort =
                evtArgs?.currentPort
                || edge?.getTargetPortId?.()
                || target?.port;

            if (targetCellId !== node.id) return;
            if (String(targetPort) !== PAYLOAD_PORT_ID) return;
            if ((edge as any).__pvPayload) return;
            (edge as any).__pvPayload = true;

            const prev = node.getData() as any;
            node.setData(
                {
                    ...prev,
                    inputs: {
                        ...(prev?.inputs || {}),
                        payload: { extractPath: '$' },
                    },
                },
                { overwrite: true },
            );

            setTimeout(() => {
                try {
                    edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
                } catch {
                    /* ignore */
                }
                delete (edge as any).__pvPayload;
            }, 50);
        };

        const bind = () => {
            if (disposed) return;
            graph = node.model?.graph;
            if (!graph) {
                requestAnimationFrame(bind);
                return;
            }
            graph.on('edge:connected', onConnected);
        };
        bind();

        return () => {
            disposed = true;
            if (graph) graph.off('edge:connected', onConnected);
        };
    }, [node]);
}

export function hasPayloadInput(data: any): boolean {
    return !!(data?.inputs?.payload);
}

/** Header 左侧 6×6 视觉凸起 + 可选「payload」角标 */
export const PayloadEntryChrome: React.FC<{
    hasPayload: boolean;
    primaryColor: string;
    children: React.ReactNode;
    tooltipIdle?: string;
    tooltipConnected?: string;
}> = ({
    hasPayload,
    primaryColor,
    children,
    tooltipIdle = '总入口：无传参时可连线标明上下游数据流',
    tooltipConnected = '已连接总入口 payload',
}) => (
    <div style={{ position: 'relative', flexShrink: 0 }}>
        <div
            title={hasPayload ? tooltipConnected : tooltipIdle}
            style={{
                position: 'absolute',
                left: -6,
                top: '50%',
                marginTop: -3,
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: hasPayload ? primaryColor : '#bfbfbf',
                zIndex: 2,
            }}
        />
        {children}
    </div>
);

export const PayloadBadge: React.FC<{ color: string }> = ({ color }) => (
    <Text style={{ fontSize: 10, color }}>payload</Text>
);

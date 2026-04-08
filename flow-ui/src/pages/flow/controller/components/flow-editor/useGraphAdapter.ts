// ============================================================================
// useGraphAdapter Hook
// 在 React 组件中使用 GraphAdapter，管理 DSL ↔ X6 的双向同步
// ============================================================================

import { useCallback, useRef, useMemo } from 'react';
import type { Graph, Node } from '@antv/x6';
import type { FlowDsl, DslNodeType } from './types';
import {
    exportGraphToDsl,
    importDslToGraph,
    createDefaultDslNode,
    updateNodeDslData,
    addSwitchCasePort,
    removeSwitchCasePort,
} from './adapter';

export interface UseGraphAdapterReturn {
    /** Import DSL JSON into graph */
    importDsl: (graph: Graph, dsl: FlowDsl) => void;
    /** Export graph to DSL JSON */
    exportDsl: (graph: Graph, flowId?: string) => FlowDsl;
    /** Export graph as formatted JSON string */
    exportDslString: (graph: Graph, flowId?: string) => string;
    /** Parse DSL JSON string, returns null on failure */
    parseDslString: (json: string) => FlowDsl | null;
    /** Add a new default node to graph */
    addNode: (graph: Graph, type: DslNodeType, position?: { x: number; y: number }) => Node | null;
    /** Update node DSL data from property panel */
    updateNodeData: (node: Node, data: Record<string, any>) => void;
    /** Get node's DSL type */
    getNodeType: (node: Node) => DslNodeType | null;
    /** Get node's DSL business data (without internal fields) */
    getNodeDslData: (node: Node) => Record<string, any>;
    /** Add switch case port */
    addSwitchCase: (node: Node, caseValue: string) => void;
    /** Remove switch case port */
    removeSwitchCase: (node: Node, caseValue: string) => void;
    /** Last exported DSL reference (for diff checking) */
    lastExportedRef: React.MutableRefObject<string>;
}

export function useGraphAdapter(): UseGraphAdapterReturn {
    const lastExportedRef = useRef<string>('');

    const importDsl = useCallback((graph: Graph, dsl: FlowDsl) => {
        importDslToGraph(graph, dsl);
    }, []);

    const exportDsl = useCallback((graph: Graph, flowId?: string): FlowDsl => {
        return exportGraphToDsl(graph, flowId);
    }, []);

    const exportDslString = useCallback(
        (graph: Graph, flowId?: string): string => {
            const dsl = exportGraphToDsl(graph, flowId);
            const json = JSON.stringify(dsl, null, 2);
            lastExportedRef.current = json;
            return json;
        },
        [],
    );

    const parseDslString = useCallback((json: string): FlowDsl | null => {
        try {
            const parsed = JSON.parse(json.trim());
            if (!parsed || typeof parsed !== 'object') return null;
            // Ensure basic shape
            if (!Array.isArray(parsed.nodes)) parsed.nodes = [];
            if (!Array.isArray(parsed.edges)) parsed.edges = [];
            return parsed as FlowDsl;
        } catch {
            return null;
        }
    }, []);

    const addNode = useCallback(
        (graph: Graph, type: DslNodeType, position?: { x: number; y: number }): Node | null => {
            const dslNode = createDefaultDslNode(type, position);
            // Import just this single node by creating a mini DSL
            const miniDsl: FlowDsl = { nodes: [dslNode], edges: [] };
            // We can't use importDslToGraph (it clears), so add the node directly
            const { importDslToGraph: _unused, ...rest } = {} as any;

            // Add node directly by reusing adapter logic
            const nodeType = dslNode.type;
            const NODE_COLORS: Record<string, string> = {
                start: '#52c41a', end: '#8c8c8c', evaluate: '#1677ff',
                if: '#ff4d4f', switch: '#722ed1', serviceCall: '#13c2c2',
                httpRequest: '#fa8c16', record: '#2f54eb',
                response: '#8c8c8c', request: '#52c41a', template: '#9254de',
                for: '#7c3aed', collect: '#0ea5e9',
            };
            const color = NODE_COLORS[nodeType] || '#595959';

            const PORT_GROUPS = {
                left: {
                    position: 'left',
                    attrs: { circle: { r: 5, magnet: true, stroke: '#5F95FF', strokeWidth: 1.5, fill: '#fff' } },
                },
                right: {
                    position: 'right',
                    attrs: { circle: { r: 5, magnet: true, stroke: '#5F95FF', strokeWidth: 1.5, fill: '#fff' } },
                },
                top: {
                    position: 'top',
                    attrs: { circle: { r: 5, magnet: true, stroke: '#5F95FF', strokeWidth: 1.5, fill: '#fff' } },
                },
                bottom: {
                    position: 'bottom',
                    attrs: { circle: { r: 5, magnet: true, stroke: '#5F95FF', strokeWidth: 1.5, fill: '#fff' } },
                },
            };

            const inferPortGroup = (portId: string): string => {
                const map: Record<string, string> = {
                    in: 'left', out: 'right', true: 'right', false: 'right',
                    item: 'right', done: 'right', default: 'right',
                    headers: 'right', params: 'right', body: 'right', list: 'right',
                };
                return map[portId] || 'right';
            };

            const getPortLabel = (portId: string): string => {
                const labels: Record<string, string> = {
                    in: 'IN', out: 'OUT', true: 'True', false: 'False',
                    item: 'Item', done: 'Done', default: 'Default',
                    headers: 'Headers', params: 'Params', body: 'Body', list: 'List',
                };
                if (portId.startsWith('case_')) return portId.substring(5);
                return labels[portId] || portId;
            };

            const getPortLabelColor = (portId: string): string => {
                if (portId === 'true' || portId === 'item') return '#52c41a';
                if (portId === 'false') return '#ff4d4f';
                if (portId === 'finish' || portId === 'default') return '#8c8c8c';
                if (portId === 'list') return '#0ea5e9';
                return '#1f1f1f';
            };

            const ports = dslNode.ports || [];
            const portItems = ports.map((p) => ({
                id: p.id,
                group: p.group || inferPortGroup(p.id),
                attrs: {
                    text: {
                        text: getPortLabel(p.id),
                        fill: getPortLabelColor(p.id),
                        fontSize: 10,
                    },
                },
            }));

            const cellData: Record<string, any> = {
                __dslType: nodeType,
                __label: nodeType,
                ...(dslNode.data || {}),
            };

            let shape = 'rect';
            let attrs: any = {
                body: { stroke: color, fill: '#ffffff', rx: 8, ry: 8, strokeWidth: 2 },
                label: { text: nodeType, fill: '#1f1f1f', fontSize: 12 },
            };

            if ((nodeType as string) === 'start') {
                shape = 'circle';
                attrs = {
                    body: { stroke: color, strokeWidth: 2, fill: '#f6ffed', r: 24 },
                    label: { text: 'Start', fill: color, fontSize: 11, fontWeight: 600 },
                };
            }

            const widths: Record<string, number> = {
                start: 60, end: 160, evaluate: 200, if: 200, switch: 200,
                serviceCall: 240, httpRequest: 240, record: 200,
                response: 160, request: 200, template: 200,
                for: 160, collect: 150,  // 与 FOR_LAYOUT / COLLECT_LAYOUT 对齐
            };
            const heights: Record<string, number> = {
                start: 60, end: 50, evaluate: 60, if: 80,
                switch: Math.max(80, 50 + ports.length * 28),
                serviceCall: 60, httpRequest: 60, record: 60,
                response: 50, request: 60, template: 60,
                for: 100, collect: 100,  // 与 FOR_LAYOUT / COLLECT_LAYOUT 对齐
            };

            const node = graph.addNode({
                id: dslNode.id,
                shape,
                x: dslNode.x || 100,
                y: dslNode.y || 100,
                width: widths[nodeType] || 200,
                height: heights[nodeType] || 60,
                attrs,
                data: cellData,
                ports: { groups: PORT_GROUPS, items: portItems },
            });

            return node;
        },
        [],
    );

    const updateNodeData = useCallback((node: Node, data: Record<string, any>) => {
        updateNodeDslData(node, data);
    }, []);

    const getNodeType = useCallback((node: Node): DslNodeType | null => {
        const data = node.getData() as any;
        return data?.__dslType || null;
    }, []);

    const getNodeDslData = useCallback((node: Node): Record<string, any> => {
        const raw = node.getData() as Record<string, any>;
        if (!raw) return {};
        const result: Record<string, any> = {};
        Object.keys(raw).forEach((k) => {
            if (!k.startsWith('__')) {
                result[k] = raw[k];
            }
        });
        return result;
    }, []);

    const addSwitchCase = useCallback((node: Node, caseValue: string) => {
        addSwitchCasePort(node, caseValue);
    }, []);

    const removeSwitchCase = useCallback((node: Node, caseValue: string) => {
        removeSwitchCasePort(node, caseValue);
    }, []);

    return useMemo(
        () => ({
            importDsl,
            exportDsl,
            exportDslString,
            parseDslString,
            addNode,
            updateNodeData,
            getNodeType,
            getNodeDslData,
            addSwitchCase,
            removeSwitchCase,
            lastExportedRef,
        }),
        [importDsl, exportDsl, exportDslString, parseDslString, addNode, updateNodeData, getNodeType, getNodeDslData, addSwitchCase, removeSwitchCase],
    );
}

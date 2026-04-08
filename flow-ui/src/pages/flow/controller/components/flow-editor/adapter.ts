// ============================================================================
// GraphAdapter: DSL V3.1 ↔ AntV X6 双向转换层
// V3.2: 所有节点特定逻辑委托给 node-registry，本文件只保留通用框架逻辑
// ============================================================================

import type { Graph, Node } from '@antv/x6';
import type { FlowDsl, DslNode, DslEdge, DslPort, DslNodeType } from './types';
import {
    getNodeColor,
    getNodeSize,
    buildNodeLabel,
    getDefaultPorts,
    getDefaultNodeData,
    getNodeRegistration,
    inferPortGroup,
    getPortLabel,
    getPortLabelColor,
    PORT_GROUPS,
} from './node-registry';

// ── 统一的连线样式配置（全局唯一，确保视觉一致） ──────────────────
export const EDGE_CONFIG = {
    /**
     * 路由器：er 正交路由
     * - offset: 'center' → 拐弯竖直段精确位于两节点的中间位置
     * - direction: 'H'   → 强制水平方向路由（匹配左右端口模型）
     * - min: 16           → 节点重叠时，拐弯点与节点至少保持 16px 距离
     */
    router: {
        name: 'er',
        args: {
            offset: 'center',
            direction: 'H',
            min: 16,
        },
    },
    /** 连接器：圆角拐弯过渡 */
    connector: { name: 'rounded', args: { radius: 10 } },
    /** 线条样式 */
    attrs: {
        line: {
            stroke: '#A2B1C3',
            strokeWidth: 2,
            targetMarker: { name: 'block', width: 10, height: 6 },
        },
    },
};

// ── DSL 内部字段白名单（导出时只保留这些） ──────────────────────
const DSL_NODE_FIELDS = new Set(['id', 'type', 'x', 'y', 'width', 'height', 'ports', 'data', 'label']);

// ============================================================================
// EXPORT: X6 Graph → DSL JSON
// ============================================================================

/**
 * 将 AntV X6 Graph 中的所有 Cell 导出为后端 DSL V3.1 格式。
 * 只保留 DSL 契约字段，过滤 X6 内部属性（zIndex, view, shape, attrs 等）。
 */
export function exportGraphToDsl(graph: Graph, flowId?: string): FlowDsl {
    const nodes: DslNode[] = [];
    const edges: DslEdge[] = [];

    // ── 导出节点 ──
    for (const cell of graph.getNodes()) {
        const pos = cell.position();
        const sz = cell.getSize();
        const cellData = cell.getData() as any;
        if (!cellData || !cellData.__dslType) continue;

        const dslNode: DslNode = {
            id: cell.id,
            type: cellData.__dslType as DslNodeType,
            x: Math.round(pos.x),
            y: Math.round(pos.y),
            width: Math.round(sz.width),
            height: Math.round(sz.height),
        };
        if (cellData.__label) {
            dslNode.label = cellData.__label;
        }

        // ── ports ──
        const rawPorts: any[] = cell.getPorts?.() || [];
        if (rawPorts.length > 0) {
            dslNode.ports = rawPorts.map((p: any) => {
                const port: DslPort = { id: p.id };
                if (p.group) port.group = p.group;
                return port;
            });
        }

        // ── data (过滤内部字段) ──
        const data = { ...cellData };
        Object.keys(data).forEach((k) => {
            if (k.startsWith('__')) delete data[k];
        });

        if (Object.keys(data).length > 0) {
            dslNode.data = data;
        }

        nodes.push(dslNode);
    }

    // ── 导出边 ──
    for (const cell of graph.getEdges()) {
        const src = cell.getSource() as any;
        const tgt = cell.getTarget() as any;
        if (!src?.cell || !tgt?.cell) continue;
        if (!src?.port || !tgt?.port) continue;

        edges.push({
            source: { cell: src.cell, port: src.port },
            target: { cell: tgt.cell, port: tgt.port },
        });
    }

    const dsl: FlowDsl = { nodes, edges };
    if (flowId) dsl.id = flowId;
    return dsl;
}

// ============================================================================
// IMPORT: DSL JSON → X6 Graph
// ============================================================================

/**
 * 将后端 DSL V3.1 JSON 导入 AntV X6 Graph。
 * 在 cell data 中存储 __dslType 和业务字段，供 export 时还原。
 */
export function importDslToGraph(graph: Graph, dsl: FlowDsl): void {
    // ── 清理之前的未决连线任务，防止被多次快速调用时产生重复连线 ──
    const anyGraph = graph as any;
    if (anyGraph.__importEdgeTimeout) {
        clearTimeout(anyGraph.__importEdgeTimeout);
        anyGraph.__importEdgeTimeout = null;
    }

    graph.clearCells();

    const autoX = 100;
    let autoY = 100;
    const autoGap = 140;

    // ── 创建 X6 节点 ──
    for (const dslNode of dsl.nodes) {
        const nodeType = dslNode.type;
        const reg = getNodeRegistration(nodeType);
        const color = getNodeColor(nodeType);

        // 计算坐标
        const x = dslNode.x ?? autoX;
        const y = dslNode.y ?? autoY;
        if (!dslNode.y) autoY += autoGap;

        // ── 规范契约：如果原始数据无 ports 或为空数组，预先推导 ──
        if (!dslNode.ports || dslNode.ports.length === 0) {
            const defaults = getDefaultPorts(nodeType) ? [...getDefaultPorts(nodeType)] : [];

            // 1. 基于 inputs 等业务数据动态补全输入桩
            if (dslNode.data && dslNode.data.inputs && typeof dslNode.data.inputs === 'object') {
                Object.keys(dslNode.data.inputs).forEach((key) => {
                    const val = dslNode.data!.inputs![key];
                    const vId = (val && typeof val === 'object' && val.id) ? val.id : key;
                    const portId = `in:var:${vId}`;
                    if (!defaults.find((p) => p.id === portId)) {
                        defaults.push({ id: portId, group: 'manual' });
                    }
                });
            }

            // 2. 特殊节点如 Switch 的动态 outputs
            if (nodeType === 'switch' && Array.isArray(dslNode.data?.cases)) {
                dslNode.data.cases.forEach((c: any) => {
                    if (c.value) {
                        const portId = `case_${c.value}`;
                        if (!defaults.find((p) => p.id === portId)) {
                            defaults.push({ id: portId, group: 'right' });
                        }
                    }
                });
            }

            dslNode.ports = defaults;
        }

        // 节点尺寸
        const ports = dslNode.ports || [];
        const defaultSize = getNodeSize(nodeType, ports);
        const width = dslNode.width ?? defaultSize.width;
        const height = dslNode.height ?? defaultSize.height;

        // ── 构建端口 items ──
        const portItems = buildPortItems(nodeType, ports, reg);

        // ── cell data ──
        const cellData: Record<string, any> = {
            __dslType: nodeType,
            __label: dslNode.label || nodeType,
            ...(dslNode.data || {}),
        };

        // ── attrs ──
        const displayLabel = buildNodeLabel(nodeType, dslNode.data || {});
        const attrs = buildNodeAttrs(nodeType, color, displayLabel, reg);

        // ── 形状名 ──
        const shape = reg?.shape.shapeName || 'rect';

        graph.addNode({
            id: dslNode.id,
            shape,
            x,
            y,
            width,
            height,
            attrs,
            data: cellData,
            ports: {
                groups: PORT_GROUPS,
                items: portItems,
            },
        });
    }

    // ── 创建 X6 边 ──
    // 延迟添加连线，因为使用 @antv/x6-react-shape 时，节点内部 DOM（含 Port 位置）
    // 由 React 异步渲染。若立刻添加连线，X6 找不到真实的 Port DOM 坐标，
    // 导致边指向节点的 (0,0) 位置。
    //
    // 策略：
    //   1. 等待 100ms 后统一创建边（增加初始等待窗口，覆盖大多数 React 首次渲染）
    //   2. 在 100ms / 350ms / 800ms 三个时间点强制「断开-重连」边端点，
    //      让 X6 重新查询 Port DOM 坐标。
    anyGraph.__importEdgeTimeout = setTimeout(() => {
        const edgeDefs = dsl.edges; // 保存原始 DSL 定义，用于重连时恢复

        const edgeCells = edgeDefs.map(dslEdge => graph.createEdge({
            source: { cell: dslEdge.source.cell, port: dslEdge.source.port },
            target: { cell: dslEdge.target.cell, port: dslEdge.target.port },
            attrs: EDGE_CONFIG.attrs,
            router: EDGE_CONFIG.router,
            connector: EDGE_CONFIG.connector,
            zIndex: 0,
        }));

        // 批量添加连线
        if (edgeCells.length > 0) {
            graph.addCell(edgeCells);

            // ── 【Workaround】强制重绘边端点 ────────────────────────────────
            // 核心思路：「断开 → 重连」迫使 X6 底层重新调用 getPortBodyElement()
            // 查询真实 Port DOM，更新 Terminal 位置矩阵。
            // 仅 setSource(src) 复用同一引用对象可能命中 X6 脏检查短路，
            // 改为先传一个临时坐标点再恢复真实端口，彻底触发重新布局。
            const forceEdgeRefresh = () => {
                graph.getEdges().forEach((edge) => {
                    const src = edge.getSource() as any;
                    const tgt = edge.getTarget() as any;
                    // 只处理已经连接到端口的边（排除手动拖拽中的半连接边）
                    if (src?.cell && src?.port && tgt?.cell && tgt?.port) {
                        // 临时断开，让 X6 将 Terminal 视为「脏」
                        edge.setSource({ x: 0, y: 0 });
                        edge.setTarget({ x: 0, y: 0 });
                        // 微任务内恢复，确保两次 set 在同一 JS 任务中紧接发生
                        Promise.resolve().then(() => {
                            edge.setSource({ cell: src.cell, port: src.port });
                            edge.setTarget({ cell: tgt.cell, port: tgt.port });
                        });
                    }
                });
            };

            // 三次兜底，覆盖 React 渲染快/中/慢 三种场景
            setTimeout(forceEdgeRefresh, 100);
            setTimeout(forceEdgeRefresh, 350);
            setTimeout(forceEdgeRefresh, 800);
        }
        anyGraph.__importEdgeTimeout = null;
    }, 100);
}

// ── 构建端口 items ──────────────────────────────────────────────
function buildPortItems(
    nodeType: DslNodeType,
    ports: DslPort[],
    reg: ReturnType<typeof getNodeRegistration>,
): any[] {
    const importCfg = reg?.importConfig;

    // manual 模式：节点提供自定义的 buildPortItems
    if (importCfg?.portMode === 'manual' && importCfg.buildPortItems) {
        return importCfg.buildPortItems(ports);
    }

    // standard 模式：通用 left/right 端口组 + 文字标签，去重
    const seenIds = new Set<string>();
    const items: any[] = [];

    for (const p of ports) {
        if (seenIds.has(p.id)) continue;
        seenIds.add(p.id);
        // 以下类型端口由 React 组件自处理渲染，adapter 不附加文字标签：
        //   in:var:*  —— If / Database 等节点的动态变量端口
        //   in:arg:*  —— SystemMethod 节点的参数端口
        //   out:*     —— 注意：这里就算有文字也由格组设置，不干涉
        const isVarPort = p.id.startsWith('in:var:') || p.id.startsWith('in:arg:');
        const item: any = {
            id: p.id,
            group: p.group || inferPortGroup(p.id),
        };
        if (!isVarPort) {
            item.attrs = {
                text: {
                    text: getPortLabel(p.id),
                    fill: getPortLabelColor(p.id),
                    fontSize: 10,
                },
            };
        }
        items.push(item);
    }
    return items;
}

// ── 构建节点 attrs ──────────────────────────────────────────────
function buildNodeAttrs(
    nodeType: DslNodeType,
    color: string,
    label: string,
    reg: ReturnType<typeof getNodeRegistration>,
): Record<string, any> {
    // 有自定义 buildAttrs 时优先使用
    if (reg?.importConfig.buildAttrs) {
        return reg.importConfig.buildAttrs(color, label);
    }

    // 默认 attrs
    return {
        body: {
            stroke: color,
            fill: '#ffffff',
            rx: 8,
            ry: 8,
            strokeWidth: 2,
        },
        label: {
            text: label,
            fill: '#1f1f1f',
            fontSize: 12,
        },
    };
}

// ============================================================================
// 创建默认 DSL 节点
// ============================================================================

let _idCounter = 0;
export function createDslNodeId(prefix: string): string {
    return `${prefix}_${Date.now()}_${(++_idCounter).toString(36)}`;
}

/**
 * 根据节点类型创建一个默认的 DslNode（含默认端口）。
 */
export function createDefaultDslNode(
    type: DslNodeType,
    position?: { x: number; y: number },
): DslNode {
    const id = createDslNodeId(type);
    const defaultPorts = getDefaultPorts(type);
    const defaultData = getDefaultNodeData(type);

    return {
        id,
        type,
        x: position?.x ?? 100,
        y: position?.y ?? 100,
        ports: defaultPorts,
        data: defaultData,
    };
}

/**
 * 向现有画布中添加单个 DSL 节点（不会清空现有 Cell）。
 * 区别于 importDslToGraph 的全量重建，此方法只新增一个节点，
 * 保留画布中已有的所有节点和连线。
 */
export function addSingleNodeToGraph(graph: Graph, dslNode: DslNode): Node {
    const nodeType = dslNode.type;
    const reg = getNodeRegistration(nodeType);
    const color = getNodeColor(nodeType);

    const x = dslNode.x ?? 100;
    const y = dslNode.y ?? 100;

    const ports = dslNode.ports || [];
    const defaultSize = getNodeSize(nodeType, ports);
    const width = dslNode.width ?? defaultSize.width;
    const height = dslNode.height ?? defaultSize.height;

    const portItems = buildPortItems(nodeType, ports, reg);

    const cellData: Record<string, any> = {
        __dslType: nodeType,
        __label: dslNode.label || nodeType,
        ...(dslNode.data || {}),
    };

    const displayLabel = buildNodeLabel(nodeType, dslNode.data || {});
    const attrs = buildNodeAttrs(nodeType, color, displayLabel, reg);
    const shape = reg?.shape.shapeName || 'rect';

    return graph.addNode({
        id: dslNode.id,
        shape,
        x,
        y,
        width,
        height,
        attrs,
        data: cellData,
        ports: {
            groups: PORT_GROUPS,
            items: portItems,
        },
    });
}

// ============================================================================
// 更新节点 Data (从配置面板→X6 Cell)
// ============================================================================

/**
 * 更新 X6 Node 的 data（保留 __dslType 等内部字段）。
 * 用于配置面板修改后回写。
 */
export function updateNodeDslData(node: Node, partialData: Record<string, any>): void {
    const existing = node.getData() as Record<string, any>;
    const merged = { ...existing, ...partialData };

    // 保留内部字段
    if (existing.__dslType) merged.__dslType = existing.__dslType;
    if (existing.__label) merged.__label = existing.__label;

    node.setData(merged, { overwrite: true });

    // 更新节点标签（SVG 节点才有 label/text attr）
    const type = existing.__dslType as DslNodeType;
    if (type) {
        const label = buildNodeLabel(type, merged);
        try { node.attr('label/text', label); } catch { /* React shape 无此 attr，忽略 */ }
    }
}

// ============================================================================
// Switch 动态端口管理
// ============================================================================

/** 为 Switch 节点添加一个 Case 端口 */
export function addSwitchCasePort(node: Node, caseValue: string): void {
    const portId = `case_${caseValue}`;
    node.addPort({
        id: portId,
        group: 'right',
        attrs: {
            text: { text: caseValue, fill: '#1f1f1f', fontSize: 10 },
        },
    });
    const ports = node.getPorts();
    node.resize(200, Math.max(80, 50 + ports.length * 28));
}

/** 移除 Switch 节点的一个 Case 端口 */
export function removeSwitchCasePort(node: Node, caseValue: string): void {
    node.removePort(`case_${caseValue}`);
    const ports = node.getPorts();
    node.resize(200, Math.max(80, 50 + ports.length * 28));
}

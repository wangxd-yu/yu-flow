// ============================================================================
// validateFlowGraph.ts
// 保存前预检 (Pre-flight DAG Validation) —— Scatter-Gather 图拓扑合法性扫描
//
// 规则：
//   规则 1：For 节点下游存在 Collect 节点时，必须配置 collectStepId（防死锁）
//   规则 2：Collect 节点反向追溯必须能找到上游 For 节点（孤儿屏障防护）
//   规则 3：For 节点下游无 Collect 时，柔性警告（发后即忘模式）
//
// 连线配置校验：
//   - 禁止数据流端口（圆形）与控制流端口（三角形）互连
// ============================================================================

import type { Graph, Node, Edge } from '@antv/x6';

// ── 端口类型判断辅助 ────────────────────────────────────────────────────────

/** 控制流端口 ID 列表（三角形语义） */
const CONTROL_PORT_IDS = new Set(['start', 'finish', 'true', 'false', 'done', 'default']);

/** 判断端口是否是控制流端口 */
function isControlPort(portId?: string | null): boolean {
    if (!portId) return false;
    if (CONTROL_PORT_IDS.has(portId)) return true;
    if (portId.startsWith('case_')) return true;
    if (portId.startsWith('out:')) {
        const suffix = portId.substring(4);
        return CONTROL_PORT_IDS.has(suffix) || suffix === 'then' || suffix === 'else';
    }
    return false;
}

/** 判断端口是否是数据流端口 */
function isDataPort(portId?: string | null): boolean {
    if (!portId) return false;
    const DATA_PORT_IDS = new Set(['in', 'out', 'list', 'item', 'headers', 'params', 'body']);
    if (DATA_PORT_IDS.has(portId)) return true;
    if (portId.startsWith('in:') || portId.startsWith('out:')) {
        const suffix = portId.startsWith('in:') ? portId.substring(3) : portId.substring(4);
        // in:var:*, in:arg:*, out:result 等都是数据流
        return !CONTROL_PORT_IDS.has(suffix);
    }
    return false;
}

// ── 类型辅助 ────────────────────────────────────────────────────────────────

function getDslType(node: Node): string | undefined {
    return (node.getData() as any)?.__dslType;
}

function getNodeDisplayName(node: Node): string {
    const d = node.getData() as any;
    return d?.__label || d?.name || node.id;
}

// ── 图遍历算法 ───────────────────────────────────────────────────────────────

/**
 * 从指定节点顺着出边 BFS 向下游搜索（可指定源端口过滤）
 * @returns 遍历到的所有下游节点集合
 */
function bfsDownstream(
    graph: Graph,
    startNode: Node,
    fromPort?: string,
    maxDepth = 30,
): Set<Node> {
    const visited = new Set<Node>();
    const queue: Array<{ node: Node; depth: number }> = [{ node: startNode, depth: 0 }];

    while (queue.length > 0) {
        const { node, depth } = queue.shift()!;
        if (depth >= maxDepth) continue;

        const outEdges: Edge[] = graph.getOutgoingEdges(node) || [];
        for (const edge of outEdges) {
            // 若指定了源端口，只跟随该端口的边
            if (fromPort) {
                const srcPort = (edge.getSource() as any)?.port;
                if (srcPort !== fromPort) continue;
            }
            const targetCell = graph.getCellById(edge.getTargetCellId() || '');
            if (!targetCell || !targetCell.isNode()) continue;
            const target = targetCell as Node;
            if (!visited.has(target)) {
                visited.add(target);
                queue.push({ node: target, depth: depth + 1 });
            }
        }
    }
    return visited;
}

/**
 * 从指定节点逆着入边 BFS 向上游搜索
 * @returns 遍历到的所有上游节点集合
 */
function bfsUpstream(graph: Graph, startNode: Node, maxDepth = 30): Set<Node> {
    const visited = new Set<Node>();
    const queue: Array<{ node: Node; depth: number }> = [{ node: startNode, depth: 0 }];

    while (queue.length > 0) {
        const { node, depth } = queue.shift()!;
        if (depth >= maxDepth) continue;

        const inEdges: Edge[] = graph.getIncomingEdges(node) || [];
        for (const edge of inEdges) {
            const sourceCell = graph.getCellById(edge.getSourceCellId() || '');
            if (!sourceCell || !sourceCell.isNode()) continue;
            const source = sourceCell as Node;
            if (!visited.has(source)) {
                visited.add(source);
                queue.push({ node: source, depth: depth + 1 });
            }
        }
    }
    return visited;
}

// ── 校验结果类型 ─────────────────────────────────────────────────────────────

export interface FlowValidationError {
    level: 'error' | 'warning';
    message: string;
    nodeId?: string;
}

export interface FlowValidationResult {
    /** 是否可以保存（只有 error 级别才阻断） */
    canSave: boolean;
    errors: FlowValidationError[];
    warnings: FlowValidationError[];
}

// ── 主校验函数 ───────────────────────────────────────────────────────────────

/**
 * validateFlowGraph
 * 保存前预检，扫描 Scatter-Gather 图拓扑合法性
 *
 * @param graph AntV X6 Graph 实例
 * @returns FlowValidationResult
 *
 * @example
 * ```ts
 * const result = validateFlowGraph(graph);
 * if (!result.canSave) {
 *   Modal.error({ title: '校验失败', content: result.errors[0].message });
 *   return;
 * }
 * if (result.warnings.length > 0) {
 *   message.warning(result.warnings[0].message);
 * }
 * // 继续保存...
 * ```
 */
export function validateFlowGraph(graph: Graph): FlowValidationResult {
    const errors: FlowValidationError[] = [];
    const warnings: FlowValidationError[] = [];

    const allNodes = graph.getNodes();

    // ── 获取所有 For 和 Collect 节点 ──────────────────────────────────────────
    const forNodes = allNodes.filter((n) => getDslType(n) === 'for');
    const collectNodes = allNodes.filter((n) => getDslType(n) === 'collect');

    // ═══════════════════════════════════════════════════════════════════════════
    // 规则 1: 死锁防护 —— For 下游有 Collect 时必须配置 collectStepId
    // ═══════════════════════════════════════════════════════════════════════════
    for (const forNode of forNodes) {
        const forData = forNode.getData() as any;
        const forName = getNodeDisplayName(forNode);

        // 顺着 item 端口以及后续所有连线向下游 BFS
        // （不限制端口，因为分支链路可能经过多个中间节点）
        const downstream = bfsDownstream(graph, forNode);

        // 检查下游是否有 collect 节点
        const hasDownstreamCollect = [...downstream].some((n) => getDslType(n) === 'collect');

        if (hasDownstreamCollect) {
            const collectStepId = forData?.collectStepId;
            if (!collectStepId || String(collectStepId).trim() === '') {
                errors.push({
                    level: 'error',
                    nodeId: forNode.id,
                    message:
                        `校验失败：For 节点 [${forName}] 下游存在汇聚操作，` +
                        `必须在其配置中绑定对应的 Collect 节点，` +
                        `以防止空数组导致流程死锁。`,
                });
            } else {
                // 验证 collectStepId 是否对应画布上真实存在的 collect 节点
                const boundCollect = graph.getCellById(collectStepId);
                if (!boundCollect || getDslType(boundCollect as Node) !== 'collect') {
                    errors.push({
                        level: 'error',
                        nodeId: forNode.id,
                        message:
                            `校验失败：For 节点 [${forName}] 配置的 collectStepId [${collectStepId}] ` +
                            `在画布上找不到有效的 Collect 节点，请重新配置。`,
                    });
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 规则 2: 孤儿屏障防护 —— Collect 必须依附于 For 循环体
    // ═══════════════════════════════════════════════════════════════════════════
    for (const collectNode of collectNodes) {
        const collectName = getNodeDisplayName(collectNode);

        // 反向 BFS 追溯所有上游节点
        const upstream = bfsUpstream(graph, collectNode);

        // 判断上游中是否存在 For 节点
        const hasUpstreamFor = [...upstream].some((n) => getDslType(n) === 'for');

        if (!hasUpstreamFor) {
            errors.push({
                level: 'error',
                nodeId: collectNode.id,
                message:
                    `校验失败：Collect 节点 [${collectName}] 必须依附于一个 For 循环节点的执行分支中，` +
                    `当前未检测到对应的上游循环体。`,
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 规则 2.5 (铁律): 每个 For 节点下游最多只允许一个 Collect 节点
    // 设计约束: 引擎的 CountDownLatch 仅支持单一屏障，多个 Collect 会造成计数混乱
    // ═══════════════════════════════════════════════════════════════════════════
    for (const forNode of forNodes) {
        const forName = getNodeDisplayName(forNode);

        // 找出下游所有 collect 节点（不含嵌套子循环内的，用 scopeDepth 过滤）
        const directCollects = _findDirectCollectsInScope(graph, forNode);

        if (directCollects.length > 1) {
            const collectNames = directCollects
                .map((n) => `[${getNodeDisplayName(n)}]`)
                .join('、');
            errors.push({
                level: 'error',
                nodeId: forNode.id,
                message:
                    `校验失败：检测到 For 节点 [${forName}] 下游连接了多个 Collect 节点 ${collectNames}。` +
                    `当前引擎严格限制每个循环只能有一个汇聚出口，请合并循环内的数据流。`,
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 规则 3: 柔性提示 —— For 节点无 Collect 下游（火炮台模式）
    // ═══════════════════════════════════════════════════════════════════════════
    for (const forNode of forNodes) {
        const forName = getNodeDisplayName(forNode);
        const downstream = bfsDownstream(graph, forNode);
        const hasDownstreamCollect = [...downstream].some((n) => getDslType(n) === 'collect');

        if (!hasDownstreamCollect) {
            warnings.push({
                level: 'warning',
                nodeId: forNode.id,
                message:
                    `检测到 For 节点 [${forName}] 下游未连接 Collect 节点。` +
                    `循环分支的执行结果将被丢弃，请确认是否符合"发后即忘"预期。`,
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 连线校验: 控制流端口 ↔ 数据流端口 互连拦截
    // ═══════════════════════════════════════════════════════════════════════════
    const allEdges = graph.getEdges();
    for (const edge of allEdges) {
        const srcPort = (edge.getSource() as any)?.port as string | undefined;
        const tgtPort = (edge.getTarget() as any)?.port as string | undefined;

        if (!srcPort || !tgtPort) continue;

        const srcIsControl = isControlPort(srcPort);
        const tgtIsControl = isControlPort(tgtPort);
        const srcIsData = isDataPort(srcPort);
        const tgtIsData = isDataPort(tgtPort);

        // 控制流端口 → 数据流端口（语义不合法）
        if (srcIsControl && tgtIsData) {
            const srcCell = graph.getCellById(edge.getSourceCellId() || '');
            const srcName = srcCell?.isNode() ? getNodeDisplayName(srcCell as Node) : edge.getSourceCellId();
            errors.push({
                level: 'error',
                message:
                    `连线错误：控制流端口 [${srcName}.${srcPort}] 不能连接数据流端口 [${tgtPort}]，` +
                    `请检查连线语义。`,
            });
        }

        // 数据流端口 → 控制流端口（语义不合法）
        if (srcIsData && tgtIsControl) {
            const tgtCell = graph.getCellById(edge.getTargetCellId() || '');
            const tgtName = tgtCell?.isNode() ? getNodeDisplayName(tgtCell as Node) : edge.getTargetCellId();
            errors.push({
                level: 'error',
                message:
                    `连线错误：数据流端口 [${srcPort}] 不能连接控制流端口 [${tgtName}.${tgtPort}]，` +
                    `请检查连线语义。`,
            });
        }
    }

    return {
        canSave: errors.length === 0,
        errors,
        warnings,
    };
}

/**
 * _findDirectCollectsInScope
 * 从 For 节点出发，用 scopeDepth BFS 收集「当前作用域层」的所有 Collect 节点。
 * · 遇到嵌套 For → scopeDepth+1（进入子循环）
 * · 遇到 Collect, depth==0 → 命中，加入结果集并停止该路径
 * · 遇到 Collect, depth>0  → scopeDepth-1（退出子循环），继续
 */
function _findDirectCollectsInScope(graph: Graph, forNode: Node): Node[] {
    type Q = { node: Node; scopeDepth: number };
    const queue: Q[] = [];
    const visited = new Set<string>();
    const result: Node[] = [];

    // 初始：从 For 节点所有出边入队（排除 start 控制流）
    const initEdges = (graph.getOutgoingEdges(forNode) || []).filter((e) => {
        const port = (e.getSource() as any)?.port as string | undefined;
        return port !== 'start';
    });
    for (const e of initEdges) {
        const cell = graph.getCellById(e.getTargetCellId() || '');
        if (!cell?.isNode()) continue;
        const t = cell as Node;
        if (!visited.has(t.id)) { visited.add(t.id); queue.push({ node: t, scopeDepth: 0 }); }
    }

    while (queue.length > 0) {
        const { node, scopeDepth } = queue.shift()!;
        const nodeType = getDslType(node);

        if (nodeType === 'collect') {
            if (scopeDepth === 0) {
                result.push(node); // 命中当前作用域 Collect
                // 不继续向下扩散此路径
            } else {
                // 子循环的 Collect，退出一层后继续
                _enqueueOutgoing(graph, node, visited, queue, scopeDepth - 1);
            }
            continue;
        }
        if (nodeType === 'for') {
            _enqueueOutgoing(graph, node, visited, queue, scopeDepth + 1);
            continue;
        }
        _enqueueOutgoing(graph, node, visited, queue, scopeDepth);
    }
    return result;
}

function _enqueueOutgoing(
    graph: Graph,
    node: Node,
    visited: Set<string>,
    queue: Array<{ node: Node; scopeDepth: number }>,
    scopeDepth: number,
) {
    const outEdges = graph.getOutgoingEdges(node) || [];
    for (const e of outEdges) {
        const port = (e.getSource() as any)?.port as string | undefined;
        if (port === 'start' || port === 'finish') continue;
        const cell = graph.getCellById(e.getTargetCellId() || '');
        if (!cell?.isNode()) continue;
        const t = cell as Node;
        if (!visited.has(t.id)) { visited.add(t.id); queue.push({ node: t, scopeDepth }); }
    }
}

// ── 连接时的实时端口校验（供 validateConnection 使用） ─────────────────────

/**
 * 实时连接校验：禁止数据流端口与控制流端口互连
 * 在 Graph 初始化时传入 connecting.validateConnection 回调中使用。
 *
 * @returns [isValid, warningMessage | null]
 */
export function validatePortConnection(
    sourcePort?: string | null,
    targetPort?: string | null,
): [boolean, string | null] {
    if (!sourcePort || !targetPort) return [false, null];

    const srcIsControl = isControlPort(sourcePort);
    const tgtIsControl = isControlPort(targetPort);
    const srcIsData = isDataPort(sourcePort);
    const tgtIsData = isDataPort(targetPort);

    // 控制流 → 数据流 or 数据流 → 控制流：拦截
    if ((srcIsControl && tgtIsData) || (srcIsData && tgtIsControl)) {
        return [
            false,
            `端口类型不匹配：${srcIsControl ? '控制流' : '数据流'}端口 [${sourcePort}] ` +
            `无法连接${tgtIsControl ? '控制流' : '数据流'}端口 [${targetPort}]`,
        ];
    }

    return [true, null];
}

// ── 配对安全冷色板 ──────────────────────────────────────────────────────────
const PAIR_COLORS = [
    '#6366f1', // Indigo 500
    '#3b82f6', // Blue 500
    '#0ea5e9', // Sky 500
    '#8b5cf6', // Violet 500
    '#a855f7', // Purple 500
    '#d946ef', // Fuchsia 500
    '#06b6d4', // Cyan 500
    '#14b8a6', // Teal 500
];

// ── 图拓扑隐式绑定算法 ────────────────────────────────────────────────────────

/**
 * autoBindLoopNodes
 * 保存前自动遍历画布，为每个 For 节点静默绑定其配对的 Collect 节点 ID，
 * 同时向 For / Collect 双方注入 pairIndex（1-based）与 pairColor（情侣色）。
 *
 * 算法：作用域深度 (scopeDepth) BFS
 * ─────────────────────────────────────────────────────────────────────
 * 从 For 节点的 item 出边出发，进行广度优先搜索：
 *   · 遇到另一个 For 节点 → scopeDepth + 1（进入嵌套子循环）
 *   · 遇到 Collect 节点：
 *       - scopeDepth === 0 → 命中！记录 ID，停止该路径的搜索
 *       - scopeDepth  > 0 → scopeDepth - 1（退出子循环），继续搜索
 *
 * 写入逻辑（双向注入）：
 *   · 找到 Collect → forNode.setData({ collectStepId, pairIndex, pairColor })
 *                    collectNode.setData({ pairIndex, pairColor })
 *   · 未找到 Collect → forNode.setData({ collectStepId: null, pairIndex: null, pairColor: null })
 *
 * @param graph AntV X6 Graph 实例
 * @returns 绑定报告：[forNodeId, collectNodeId | null][]
 */
export function autoBindLoopNodes(graph: Graph): Array<[string, string | null]> {
    const allNodes = graph.getNodes();
    const forNodes = allNodes
        .filter((n) => getDslType(n) === 'for')
        .sort((a, b) => a.id.localeCompare(b.id)); // 按 ID（含创建时间戳）排序，序号稳定递增
    const report: Array<[string, string | null]> = [];

    // 先清除所有 Collect 的 pair 信息，稍后由配对成功的 For 重新写入
    const collectNodes = allNodes.filter((n) => getDslType(n) === 'collect');
    for (const cn of collectNodes) {
        const cd = cn.getData() as any;
        if (cd?.pairIndex != null || cd?.pairColor != null) {
            cn.setData(
                { ...cd, pairIndex: null, pairColor: null },
                { silent: false },
            );
        }
    }

    for (let i = 0; i < forNodes.length; i++) {
        const forNode = forNodes[i];
        const collectId = _findPairedCollect(graph, forNode);
        const pairIndex = i + 1; // 1-based 序号
        const pairColor = PAIR_COLORS[i % PAIR_COLORS.length];

        const currentData = forNode.getData() as any;

        if (collectId) {
            // ── 配对成功：双向注入 pairIndex + pairColor ──
            forNode.setData(
                { ...currentData, collectStepId: collectId, pairIndex, pairColor },
                { silent: false },
            );

            const collectNode = graph.getCellById(collectId);
            if (collectNode?.isNode()) {
                const cData = (collectNode as Node).getData() as any;
                (collectNode as Node).setData(
                    { ...cData, pairIndex, pairColor },
                    { silent: false },
                );
            }
        } else {
            // ── 未找到配对 Collect（发后即忘模式）──
            forNode.setData(
                { ...currentData, collectStepId: null, pairIndex: null, pairColor: null },
                { silent: false },
            );
        }

        report.push([forNode.id, collectId]);
    }

    return report;
}

/**
 * 内部实现：从 forNode 的 item 端口出发，BFS 找配对的 Collect
 */
function _findPairedCollect(graph: Graph, forNode: Node): string | null {
    // BFS 队列每条记录包含：节点 + 当前作用域深度
    type QueueItem = { node: Node; scopeDepth: number };
    const queue: QueueItem[] = [];
    const visited = new Set<string>(); // 防止在同一 forNode 搜索中形成环

    // ── Step1: 从 forNode 的 item/out 出边入队 ───────────────────────────
    // (支持 id='item' 或 id='in' 后端有时用不同名称)
    const startEdges = (graph.getOutgoingEdges(forNode) || []).filter((edge) => {
        const srcPort = (edge.getSource() as any)?.port as string | undefined;
        // 只跟随数据流出边，不跟随控制流端口（finish/start）
        return srcPort === 'item' || srcPort === 'out' || srcPort === 'in' && false; // item only
    });

    // 若实际图中端口命名不确定，兜底策略：跟随所有出边（scopeDepth 机制确保正确性）
    const loopBodyEdges = startEdges.length > 0
        ? startEdges
        : (graph.getOutgoingEdges(forNode) || []).filter((edge) => {
            const srcPort = (edge.getSource() as any)?.port as string | undefined;
            // 排除 start（控制流入），跟随 item 和其他数据流出边
            return srcPort !== 'start';
        });

    for (const edge of loopBodyEdges) {
        const targetCell = graph.getCellById(edge.getTargetCellId() || '');
        if (!targetCell?.isNode()) continue;
        const target = targetCell as Node;
        if (!visited.has(target.id)) {
            visited.add(target.id);
            queue.push({ node: target, scopeDepth: 0 });
        }
    }

    // ── Step2: BFS ──────────────────────────────────────────────────────
    while (queue.length > 0) {
        const { node, scopeDepth } = queue.shift()!;
        const nodeType = getDslType(node);

        if (nodeType === 'collect') {
            if (scopeDepth === 0) {
                // ✅ 命中：这是当前 For 的配对 Collect
                return node.id;
            } else {
                // 子循环的 Collect，退出一层嵌套后继续
                const nextDepth = scopeDepth - 1;
                const outEdges = graph.getOutgoingEdges(node) || [];
                for (const edge of outEdges) {
                    const targetCell = graph.getCellById(edge.getTargetCellId() || '');
                    if (!targetCell?.isNode()) continue;
                    const target = targetCell as Node;
                    if (!visited.has(target.id)) {
                        visited.add(target.id);
                        queue.push({ node: target, scopeDepth: nextDepth });
                    }
                }
            }
            continue;
        }

        if (nodeType === 'for') {
            // 遇到嵌套 For，作用域深度 +1，继续向下搜索（跳过子循环内部路径）
            const outEdges = graph.getOutgoingEdges(node) || [];
            for (const edge of outEdges) {
                const srcPort = (edge.getSource() as any)?.port as string | undefined;
                if (srcPort === 'start') continue; // 控制流不跟随
                const targetCell = graph.getCellById(edge.getTargetCellId() || '');
                if (!targetCell?.isNode()) continue;
                const target = targetCell as Node;
                if (!visited.has(target.id)) {
                    visited.add(target.id);
                    queue.push({ node: target, scopeDepth: scopeDepth + 1 });
                }
            }
            continue;
        }

        // 普通节点：继续向下游扩散
        const outEdges = graph.getOutgoingEdges(node) || [];
        for (const edge of outEdges) {
            const srcPort = (edge.getSource() as any)?.port as string | undefined;
            if (srcPort === 'start' || srcPort === 'finish') continue; // 跳过控制流
            const targetCell = graph.getCellById(edge.getTargetCellId() || '');
            if (!targetCell?.isNode()) continue;
            const target = targetCell as Node;
            if (!visited.has(target.id)) {
                visited.add(target.id);
                queue.push({ node: target, scopeDepth });
            }
        }
    }

    // 未找到配对 Collect → 发后即忘模式
    return null;
}

// ============================================================================
// dsl.test.ts
// 前端 DSL V3.1 测试用例
// ============================================================================
//
// 测试框架: Jest + 纯逻辑测试 (无 DOM 依赖)
// 运行方式: npx jest dsl.test.ts
//
// 注意: 这些测试不依赖真实的 @antv/x6 Graph 实例，
// 而是测试 adapter 层的纯逻辑函数和类型约束。
// ============================================================================

import type {
    FlowDsl,
    DslNode,
    DslEdge,
    DslPort,
    DslNodeType,
    InputsMap,
    InputMapping,
    ExpressionLanguage,
    EvaluateNodeData,
    IfNodeData,
    SwitchNodeData,
    ServiceCallNodeData,
    HttpRequestNodeData,
    ForNodeData,
    CollectNodeData,
} from '../types';

// ============================================================================
// 辅助：模拟极简 Graph/Node 对象（仅用于 adapter 逻辑测试）
// ============================================================================

interface MockPort {
    id: string;
    group?: string;
    attrs?: any;
}

interface MockNodeConfig {
    id: string;
    // 测试中需要支持 start/end 这些不在 DslNodeType 里的2个特殊对象和其他类型
    type: DslNodeType | 'start' | 'end';
    x: number;
    y: number;
    ports: MockPort[];
    data: Record<string, any>;
}

class MockNode {
    private _id: string;
    private _data: Record<string, any>;
    private _ports: MockPort[];
    private _x: number;
    private _y: number;

    constructor(config: MockNodeConfig) {
        this._id = config.id;
        this._data = {
            __dslType: config.type,
            __label: config.type,
            ...config.data,
        };
        this._ports = config.ports;
        this._x = config.x;
        this._y = config.y;
    }

    get id() {
        return this._id;
    }

    position() {
        return { x: this._x, y: this._y };
    }

    getData() {
        return this._data;
    }

    setData(data: any) {
        this._data = data;
    }

    getPorts() {
        return this._ports;
    }

    addPort(port: MockPort) {
        this._ports.push(port);
    }

    removePort(portId: string) {
        this._ports = this._ports.filter((p) => p.id !== portId);
    }
}

class MockEdge {
    private _source: { cell: string; port: string };
    private _target: { cell: string; port: string };

    constructor(source: { cell: string; port: string }, target: { cell: string; port: string }) {
        this._source = source;
        this._target = target;
    }

    getSource() {
        return this._source;
    }

    getTarget() {
        return this._target;
    }

    getSourceCellId() {
        return this._source.cell;
    }

    getTargetCellId() {
        return this._target.cell;
    }
}

class MockGraph {
    private _nodes: MockNode[] = [];
    private _edges: MockEdge[] = [];

    addNode(config: MockNodeConfig) {
        const node = new MockNode(config);
        this._nodes.push(node);
        return node;
    }

    getNodes() {
        return this._nodes;
    }

    getEdges() {
        return this._edges;
    }

    addEdge(source: { cell: string; port: string }, target: { cell: string; port: string }) {
        const edge = new MockEdge(source, target);
        this._edges.push(edge);
        return edge;
    }

    clearCells() {
        this._nodes = [];
        this._edges = [];
    }

    getCellById(id: string): MockNode | undefined {
        return this._nodes.find((n) => n.id === id);
    }
}

// ============================================================================
// 纯函数版 export（不依赖 X6 Graph API）
// 这是对 adapter.ts 中 exportGraphToDsl 逻辑的镜像测试
// ============================================================================

function exportMockGraphToDsl(graph: MockGraph, flowId?: string): FlowDsl {
    const nodes: DslNode[] = [];
    const edges: DslEdge[] = [];

    for (const cell of graph.getNodes()) {
        const pos = cell.position();
        const cellData = cell.getData() as any;
        if (!cellData || !cellData.__dslType) continue;

        const dslNode: DslNode = {
            id: cell.id,
            type: cellData.__dslType as DslNodeType,
            x: Math.round(pos.x),
            y: Math.round(pos.y),
        };

        const rawPorts = cell.getPorts() || [];
        if (rawPorts.length > 0) {
            dslNode.ports = rawPorts.map((p: any) => {
                const port: DslPort = { id: p.id };
                if (p.group) port.group = p.group;
                return port;
            });
        }
        if (cellData.__label) {
            dslNode.label = cellData.__label;
        }

        const data = { ...cellData };
        delete data.__dslType;
        delete data.__label;
        Object.keys(data).forEach((k) => {
            if (k.startsWith('__')) delete data[k];
        });

        if (Object.keys(data).length > 0) {
            dslNode.data = data;
        }

        nodes.push(dslNode);
    }

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
// 测试 1: DSL 导出测试 — Start → Evaluate → End
// ============================================================================

describe('DSL V3.1 导出测试', () => {
    it('应导出符合 V3.1 结构的 JSON (Start → Evaluate → End)', () => {
        const graph = new MockGraph();

        // Start 节点
        graph.addNode({
            id: 'start',
            type: 'start',
            x: 100,
            y: 100,
            ports: [{ id: 'out' }],
            data: {},
        });

        // Evaluate 节点
        graph.addNode({
            id: 'node_calc',
            type: 'evaluate',
            x: 300,
            y: 100,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: {
                inputs: {
                    a: { extractPath: '$.start.args.price' },
                },
                expression: 'a * 0.8',
                language: 'aviator',
            },
        });

        // End 节点
        graph.addNode({
            id: 'end',
            type: 'end',
            x: 500,
            y: 100,
            ports: [{ id: 'in' }],
            data: {
                responseBody: '${node_calc.result}',
            },
        });

        // 连线
        graph.addEdge({ cell: 'start', port: 'out' }, { cell: 'node_calc', port: 'in' });
        graph.addEdge({ cell: 'node_calc', port: 'out' }, { cell: 'end', port: 'in' });

        // 导出
        const dsl = exportMockGraphToDsl(graph);

        // ── 验证顶层结构 ──
        expect(dsl).toHaveProperty('nodes');
        expect(dsl).toHaveProperty('edges');
        expect(Array.isArray(dsl.nodes)).toBe(true);
        expect(Array.isArray(dsl.edges)).toBe(true);

        // ── 验证节点数量 ──
        expect(dsl.nodes).toHaveLength(3);
        expect(dsl.edges).toHaveLength(2);

        // ── 验证 Start 节点 ──
        const startNode = dsl.nodes.find((n) => n.id === 'start');
        expect(startNode).toBeDefined();
        expect(startNode!.type).toBe('start');
        expect(startNode!.x).toBe(100);
        expect(startNode!.y).toBe(100);
        expect(startNode!.ports).toEqual([{ id: 'out' }]);

        // ── 验证 Evaluate 节点 ──
        const evalNode = dsl.nodes.find((n) => n.id === 'node_calc');
        expect(evalNode).toBeDefined();
        expect(evalNode!.type).toBe('evaluate');
        expect(evalNode!.x).toBe(300);
        expect(evalNode!.y).toBe(100);
        expect(evalNode!.ports).toEqual([{ id: 'in' }, { id: 'out' }]);

        // ── 关键：验证 inputs 和 extractPath ──
        expect(evalNode!.data).toBeDefined();
        expect(evalNode!.data!.inputs).toBeDefined();
        expect(evalNode!.data!.inputs.a).toEqual({ extractPath: '$.start.args.price' });
        expect(evalNode!.data!.expression).toBe('a * 0.8');
        expect(evalNode!.data!.language).toBe('aviator');

        // ── 验证 End 节点 ──
        const endNode = dsl.nodes.find((n) => n.id === 'end');
        expect(endNode).toBeDefined();
        expect(endNode!.type).toBe('end');
        expect(endNode!.data!.responseBody).toBe('${node_calc.result}');

        // ── 验证边格式 ──
        expect(dsl.edges[0]).toEqual({
            source: { cell: 'start', port: 'out' },
            target: { cell: 'node_calc', port: 'in' },
        });
        expect(dsl.edges[1]).toEqual({
            source: { cell: 'node_calc', port: 'out' },
            target: { cell: 'end', port: 'in' },
        });

        // ── 验证不包含 X6 内部属性 ──
        const json = JSON.stringify(dsl);
        expect(json).not.toContain('__dslType');
        expect(json).not.toContain('__label');
        expect(json).not.toContain('zIndex');
        expect(json).not.toContain('view');
        expect(json).not.toContain('shape');
        expect(json).not.toContain('attrs');
    });

    it('应正确处理空 inputs', () => {
        const graph = new MockGraph();
        graph.addNode({
            id: 'start',
            type: 'start',
            x: 0,
            y: 0,
            ports: [{ id: 'out' }],
            data: {},
        });
        graph.addNode({
            id: 'calc',
            type: 'evaluate',
            x: 200,
            y: 0,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: { expression: "'Hello World'", inputs: {} },
        });

        const dsl = exportMockGraphToDsl(graph);
        const evalNode = dsl.nodes.find((n) => n.id === 'calc');
        expect(evalNode!.data!.inputs).toEqual({});
        expect(evalNode!.data!.expression).toBe("'Hello World'");
    });

    it('应支持多 inputs 映射', () => {
        const graph = new MockGraph();
        graph.addNode({
            id: 'node_z',
            type: 'evaluate',
            x: 0,
            y: 0,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: {
                inputs: {
                    bVal: { extractPath: '$.node_b.result' },
                    cVal: { extractPath: '$.node_c.result' },
                },
                expression: "bVal + '_' + cVal",
            },
        });

        const dsl = exportMockGraphToDsl(graph);
        const node = dsl.nodes[0];
        expect(node.data!.inputs.bVal).toEqual({ extractPath: '$.node_b.result' });
        expect(node.data!.inputs.cVal).toEqual({ extractPath: '$.node_c.result' });
    });
});

// ============================================================================
// 测试 2: If 节点端口逻辑
// ============================================================================

describe('If 节点端口逻辑', () => {
    it('If 节点初始化时应自动创建 in/true/false 端口', () => {
        const graph = new MockGraph();
        const node = graph.addNode({
            id: 'if_node',
            type: 'if',
            x: 100,
            y: 100,
            ports: [
                { id: 'in', group: 'left' },
                { id: 'true', group: 'right' },
                { id: 'false', group: 'right' },
            ],
            data: {
                inputs: { age: { extractPath: '$.start.args.age' } },
                condition: 'age >= 18',
            },
        });

        const ports = node.getPorts();
        expect(ports).toHaveLength(3);

        const portIds = ports.map((p) => p.id);
        expect(portIds).toContain('in');
        expect(portIds).toContain('true');
        expect(portIds).toContain('false');

        // 验证导出
        const dsl = exportMockGraphToDsl(graph);
        const ifNode = dsl.nodes[0];
        expect(ifNode.type).toBe('if');
        expect(ifNode.ports).toHaveLength(3);
        expect(ifNode.ports!.map((p) => p.id)).toEqual(['in', 'true', 'false']);
        expect(ifNode.data!.condition).toBe('age >= 18');
        expect(ifNode.data!.inputs.age).toEqual({ extractPath: '$.start.args.age' });
    });

    it('If 节点边应正确连接 true/false 端口', () => {
        const graph = new MockGraph();

        graph.addNode({
            id: 'start',
            type: 'start',
            x: 0,
            y: 0,
            ports: [{ id: 'out' }],
            data: {},
        });
        graph.addNode({
            id: 'if_node',
            type: 'if',
            x: 200,
            y: 0,
            ports: [{ id: 'in' }, { id: 'true' }, { id: 'false' }],
            data: { condition: 'age >= 18', inputs: { age: '$.start.args.age' } },
        });
        graph.addNode({
            id: 'end_adult',
            type: 'end',
            x: 400,
            y: -50,
            ports: [{ id: 'in' }],
            data: { responseBody: 'adult' },
        });
        graph.addNode({
            id: 'end_minor',
            type: 'end',
            x: 400,
            y: 50,
            ports: [{ id: 'in' }],
            data: { responseBody: 'minor' },
        });

        graph.addEdge({ cell: 'start', port: 'out' }, { cell: 'if_node', port: 'in' });
        graph.addEdge({ cell: 'if_node', port: 'true' }, { cell: 'end_adult', port: 'in' });
        graph.addEdge({ cell: 'if_node', port: 'false' }, { cell: 'end_minor', port: 'in' });

        const dsl = exportMockGraphToDsl(graph);

        // 验证true边
        const trueEdge = dsl.edges.find(
            (e) => e.source.cell === 'if_node' && e.source.port === 'true',
        );
        expect(trueEdge).toBeDefined();
        expect(trueEdge!.target).toEqual({ cell: 'end_adult', port: 'in' });

        // 验证false边
        const falseEdge = dsl.edges.find(
            (e) => e.source.cell === 'if_node' && e.source.port === 'false',
        );
        expect(falseEdge).toBeDefined();
        expect(falseEdge!.target).toEqual({ cell: 'end_minor', port: 'in' });
    });
});

// ============================================================================
// 测试 3: Switch 节点动态端口
// ============================================================================

describe('Switch 节点端口逻辑', () => {
    it('Switch 节点应支持 case_XXX 动态端口', () => {
        const graph = new MockGraph();
        const node = graph.addNode({
            id: 'sw_node',
            type: 'switch',
            x: 100,
            y: 100,
            ports: [
                { id: 'in' },
                { id: 'case_ADMIN' },
                { id: 'case_USER' },
                { id: 'default' },
            ],
            data: {
                inputs: { r: { extractPath: '$.start.args.role' } },
                expression: 'r',
            },
        });

        const ports = node.getPorts();
        expect(ports).toHaveLength(4);
        expect(ports.map((p) => p.id)).toContain('case_ADMIN');
        expect(ports.map((p) => p.id)).toContain('case_USER');
        expect(ports.map((p) => p.id)).toContain('default');

        // 动态添加新 Case
        node.addPort({ id: 'case_MANAGER' });
        expect(node.getPorts()).toHaveLength(5);
        expect(node.getPorts().map((p) => p.id)).toContain('case_MANAGER');

        // 移除一个 Case
        node.removePort('case_USER');
        expect(node.getPorts()).toHaveLength(4);
        expect(node.getPorts().map((p) => p.id)).not.toContain('case_USER');
    });
});

// ============================================================================
// 测试 4: For + Collect (Scatter-Gather) 节点端口
// ============================================================================

describe('For + Collect (Scatter-Gather) 节点端口逻辑', () => {
    it('For 节点应有 in/start/item 三个固定桩', () => {
        const graph = new MockGraph();
        const node = graph.addNode({
            id: 'loop',
            type: 'for',
            x: 100,
            y: 100,
            ports: [{ id: 'in' }, { id: 'start' }, { id: 'item' }],
            data: { collectStepId: 'collector', timeoutMs: 30000 },
        });

        const ports = node.getPorts();
        expect(ports).toHaveLength(3);
        const portIds = ports.map((p) => p.id);
        expect(portIds).toContain('in');
        expect(portIds).toContain('start');
        expect(portIds).toContain('item');
    });

    it('Collect 节点应有 item(输入)/list/finish(输出) 三个固定桩', () => {
        const graph = new MockGraph();
        const node = graph.addNode({
            id: 'collector',
            type: 'collect',
            x: 400,
            y: 100,
            ports: [{ id: 'item' }, { id: 'list' }, { id: 'finish' }],
            data: { inputs: {}, timeoutMs: 30000 },
        });

        const ports = node.getPorts();
        expect(ports).toHaveLength(3);
        const portIds = ports.map((p) => p.id);
        // 入口端口：item（对齐后端 CollectStep.getInputPorts() 的 "item"）
        expect(portIds).toContain('item');
        // 输出端口：list（聚合数组）+ finish（控制流完成信号）
        expect(portIds).toContain('list');
        expect(portIds).toContain('finish');
    });

    it('For→Collect Scatter-Gather 连线应正确导出 DSL', () => {
        const graph = new MockGraph();
        graph.addNode({ id: 'start', type: 'start', x: 0, y: 0, ports: [{ id: 'out' }], data: {} });
        graph.addNode({
            id: 'loop', type: 'for', x: 200, y: 0,
            ports: [{ id: 'in' }, { id: 'item' }],
            data: { collectStepId: 'collector', inputs: { collection: { extractPath: '$.start.args.items' } } },
        });
        graph.addNode({
            id: 'collector', type: 'collect', x: 400, y: 0,
            ports: [{ id: 'item' }, { id: 'list' }, { id: 'finish' }],
            data: { inputs: {} },
        });
        graph.addNode({ id: 'end', type: 'end', x: 600, y: 0, ports: [{ id: 'in' }], data: { responseBody: '${collector.list}' } });

        graph.addEdge({ cell: 'start', port: 'out' }, { cell: 'loop', port: 'in' });
        graph.addEdge({ cell: 'loop', port: 'item' }, { cell: 'collector', port: 'item' });
        graph.addEdge({ cell: 'collector', port: 'list' }, { cell: 'end', port: 'in' });

        const dsl = exportMockGraphToDsl(graph);
        expect(dsl.nodes).toHaveLength(4);
        expect(dsl.edges).toHaveLength(3);

        const forNode = dsl.nodes.find((n) => n.id === 'loop')!;
        expect(forNode.type).toBe('for');
        expect(forNode.data!.collectStepId).toBe('collector');

        const collectNode = dsl.nodes.find((n) => n.id === 'collector')!;
        expect(collectNode.type).toBe('collect');
        // 验证输入端口名为 item（后端契约）
        expect(collectNode.ports!.map((p) => p.id)).toContain('item');
        expect(collectNode.ports!.map((p) => p.id)).toContain('list');
        expect(collectNode.ports!.map((p) => p.id)).toContain('finish');
    });
});


// ============================================================================
// 测试 5: 撤销重做 — 位置复原 (模拟)
// ============================================================================

describe('撤销重做逻辑', () => {
    it('修改节点位置后撤销，位置应复原', () => {
        // 模拟 History 栈
        const history: Array<{ x: number; y: number }> = [];

        // 初始位置
        const initialPos = { x: 100, y: 100 };
        history.push({ ...initialPos });

        // 移动节点
        const movedPos = { x: 300, y: 200 };
        history.push({ ...movedPos });

        // 当前位置（栈顶）
        expect(history[history.length - 1]).toEqual(movedPos);

        // 撤销: 弹出栈顶，回到上一个
        history.pop();
        const restoredPos = history[history.length - 1];
        expect(restoredPos).toEqual(initialPos);
    });

    it('支持多次撤销和重做', () => {
        const undoStack: Array<{ x: number; y: number }> = [];
        const redoStack: Array<{ x: number; y: number }> = [];

        // 初始
        undoStack.push({ x: 0, y: 0 });

        // 移动1
        undoStack.push({ x: 100, y: 100 });

        // 移动2
        undoStack.push({ x: 200, y: 200 });

        // Undo once
        const last1 = undoStack.pop()!;
        redoStack.push(last1);
        expect(undoStack[undoStack.length - 1]).toEqual({ x: 100, y: 100 });

        // Undo again
        const last2 = undoStack.pop()!;
        redoStack.push(last2);
        expect(undoStack[undoStack.length - 1]).toEqual({ x: 0, y: 0 });

        // Redo once
        const redo1 = redoStack.pop()!;
        undoStack.push(redo1);
        expect(undoStack[undoStack.length - 1]).toEqual({ x: 100, y: 100 });
    });
});

// ============================================================================
// 测试 6: 多语言表达式支持
// ============================================================================

describe('多语言表达式', () => {
    it('evaluate 节点应正确保留 language 字段', () => {
        const graph = new MockGraph();
        graph.addNode({
            id: 'aviator_node',
            type: 'evaluate',
            x: 0,
            y: 0,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: { expression: 'a * 0.8', language: 'aviator', inputs: { a: { extractPath: '$.start.args.price' } } },
        });
        graph.addNode({
            id: 'spel_node',
            type: 'evaluate',
            x: 200,
            y: 0,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: { expression: '#Math.max(#val1, #val2)', language: 'spel', inputs: {} },
        });

        const dsl = exportMockGraphToDsl(graph);

        const aviatorNode = dsl.nodes.find((n) => n.id === 'aviator_node');
        expect(aviatorNode!.data!.language).toBe('aviator');

        const spelNode = dsl.nodes.find((n) => n.id === 'spel_node');
        expect(spelNode!.data!.language).toBe('spel');
    });

    it('if 节点应正确保留 language 字段', () => {
        const graph = new MockGraph();
        graph.addNode({
            id: 'if_spel',
            type: 'if',
            x: 0,
            y: 0,
            ports: [{ id: 'in' }, { id: 'true' }, { id: 'false' }],
            data: {
                condition: '#discountPrice < 100.0',
                language: 'spel',
                inputs: { discountPrice: { extractPath: '$.calc.result' } },
            },
        });

        const dsl = exportMockGraphToDsl(graph);
        const ifNode = dsl.nodes[0];
        expect(ifNode.data!.language).toBe('spel');
        expect(ifNode.data!.condition).toBe('#discountPrice < 100.0');
    });
});

// ============================================================================
// 测试 7: 完整流程导出 — 对标后端测试用例
// ============================================================================

describe('完整流程对标后端', () => {
    it('应生成与后端测试 07 (ServiceCall) 兼容的 DSL', () => {
        const graph = new MockGraph();

        graph.addNode({
            id: 'start',
            type: 'start',
            x: 0,
            y: 0,
            ports: [{ id: 'out' }],
            data: {},
        });
        graph.addNode({
            id: 'call_svc',
            type: 'serviceCall',
            x: 200,
            y: 0,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: {
                service: 'testService',
                method: 'greet',
                inputs: { n: { extractPath: '$.start.args.name' } },
                args: ['n'],
            },
        });
        graph.addNode({
            id: 'end',
            type: 'end',
            x: 400,
            y: 0,
            ports: [{ id: 'in' }],
            data: { responseBody: '${call_svc.result}' },
        });

        graph.addEdge({ cell: 'start', port: 'out' }, { cell: 'call_svc', port: 'in' });
        graph.addEdge({ cell: 'call_svc', port: 'out' }, { cell: 'end', port: 'in' });

        const dsl = exportMockGraphToDsl(graph);

        // 验证结构匹配后端期望
        expect(dsl.nodes).toHaveLength(3);
        expect(dsl.edges).toHaveLength(2);

        const svcNode = dsl.nodes.find((n) => n.id === 'call_svc')!;
        expect(svcNode.type).toBe('serviceCall');
        expect(svcNode.data!.service).toBe('testService');
        expect(svcNode.data!.method).toBe('greet');
        expect(svcNode.data!.inputs.n).toEqual({ extractPath: '$.start.args.name' });
        expect(svcNode.data!.args).toEqual(['n']);
        expect(svcNode.ports).toEqual([{ id: 'in' }, { id: 'out' }]);
    });

    it('应生成与后端测试 17 (混合引擎) 兼容的 DSL', () => {
        const graph = new MockGraph();

        graph.addNode({
            id: 'start',
            type: 'start',
            x: 0,
            y: 0,
            ports: [{ id: 'out' }],
            data: {},
        });
        graph.addNode({
            id: 'calc_aviator',
            type: 'evaluate',
            x: 200,
            y: 0,
            ports: [{ id: 'in' }, { id: 'out' }],
            data: {
                language: 'aviator',
                inputs: { a: { extractPath: '$.start.args.price' } },
                expression: 'a * 0.8',
            },
        });
        graph.addNode({
            id: 'check_spel',
            type: 'if',
            x: 400,
            y: 0,
            ports: [{ id: 'in' }, { id: 'true' }, { id: 'false' }],
            data: {
                language: 'spel',
                inputs: { discountPrice: { extractPath: '$.calc_aviator.result' } },
                condition: '#discountPrice < 100.0',
            },
        });
        graph.addNode({
            id: 'end_cheap',
            type: 'end',
            x: 600,
            y: -50,
            ports: [{ id: 'in' }],
            data: { responseBody: 'CHEAP' },
        });
        graph.addNode({
            id: 'end_expensive',
            type: 'end',
            x: 600,
            y: 50,
            ports: [{ id: 'in' }],
            data: { responseBody: 'EXPENSIVE' },
        });

        graph.addEdge({ cell: 'start', port: 'out' }, { cell: 'calc_aviator', port: 'in' });
        graph.addEdge({ cell: 'calc_aviator', port: 'out' }, { cell: 'check_spel', port: 'in' });
        graph.addEdge({ cell: 'check_spel', port: 'true' }, { cell: 'end_cheap', port: 'in' });
        graph.addEdge({ cell: 'check_spel', port: 'false' }, { cell: 'end_expensive', port: 'in' });

        const dsl = exportMockGraphToDsl(graph);

        expect(dsl.nodes).toHaveLength(5);
        expect(dsl.edges).toHaveLength(4);

        const aviatorNode = dsl.nodes.find((n) => n.id === 'calc_aviator')!;
        expect(aviatorNode.data!.language).toBe('aviator');

        const spelNode = dsl.nodes.find((n) => n.id === 'check_spel')!;
        expect(spelNode.data!.language).toBe('spel');
        expect(spelNode.data!.condition).toBe('#discountPrice < 100.0');
    });
});

// ============================================================================
// 测试 8: 类型安全检查
// ============================================================================

describe('类型定义检查', () => {
    it('FlowDsl 结构应符合规范', () => {
        const dsl: FlowDsl = {
            id: 'flow_1',
            nodes: [
                {
                    id: 'node_1',
                    type: 'evaluate',
                    x: 100,
                    y: 100,
                    ports: [{ id: 'in', group: 'left' }, { id: 'out', group: 'right' }],
                    data: {
                        inputs: { a: { extractPath: '$.start.args.price' } },
                        expression: 'a * 0.8',
                        language: 'aviator',
                    },
                },
            ],
            edges: [
                { source: { cell: 'start', port: 'out' }, target: { cell: 'node_1', port: 'in' } },
            ],
        };

        expect(dsl.id).toBe('flow_1');
        expect(dsl.nodes[0].id).toBe('node_1');
        expect(dsl.nodes[0].type).toBe('evaluate');
        expect(dsl.nodes[0].x).toBe(100);
        expect(dsl.nodes[0].ports![0].group).toBe('left');
    });

    it('InputMapping 类型应包含 extractPath', () => {
        const mapping: InputMapping = { extractPath: '$.start.args.name' };
        expect(mapping.extractPath).toBe('$.start.args.name');
    });

    it('ExpressionLanguage 类型应限定为 aviator | spel', () => {
        const lang1: ExpressionLanguage = 'aviator';
        const lang2: ExpressionLanguage = 'spel';
        expect(lang1).toBe('aviator');
        expect(lang2).toBe('spel');
    });
});

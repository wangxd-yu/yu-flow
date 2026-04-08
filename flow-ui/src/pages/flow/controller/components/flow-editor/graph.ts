import { Edge, Graph, Node } from '@antv/x6';
import type { FlowDefinition, NodeData, Step, StepType, UiGraphLayout, VarDef, VarType } from './types-compat';
import { UI_LAYOUT_GRAPH_KEY, UI_LAYOUT_KEY } from './types-compat';
import { getNodeDefinition } from './registry';

export function normalizeToFlowDefinition(parsed: any): FlowDefinition {
  if (!parsed) return { steps: [] };
  if (Array.isArray(parsed)) return { steps: parsed as any };
  if (typeof parsed === 'object') {
    const maybe = parsed as FlowDefinition;
    if (Array.isArray(maybe.steps)) return maybe;
    return { ...maybe, steps: [] };
  }
  return { steps: [] };
}

export function readLayoutFromFlow(def: FlowDefinition): UiGraphLayout | null {
  const args = def.args || {};
  const ui = (args as any)?.[UI_LAYOUT_KEY];
  const layout = ui?.[UI_LAYOUT_GRAPH_KEY];
  if (!layout || typeof layout !== 'object') return null;
  if (layout.version !== 1) return null;
  if (!layout.nodes || typeof layout.nodes !== 'object') return null;
  if (!Array.isArray(layout.edges)) return null;
  return layout as UiGraphLayout;
}

export function writeLayoutToFlow(def: FlowDefinition, layout: UiGraphLayout): FlowDefinition {
  const args = def.args ? { ...def.args } : {};
  const ui = (args as any)[UI_LAYOUT_KEY] ? { ...(args as any)[UI_LAYOUT_KEY] } : {};
  ui[UI_LAYOUT_GRAPH_KEY] = layout;
  (args as any)[UI_LAYOUT_KEY] = ui;
  return { ...def, args };
}

export function createDefaultEdge(source: string, target: string) {
  return {
    shape: 'edge',
    source: { cell: source, port: 'out' },
    target: { cell: target, port: 'in' },
    data: { kind: 'control' },
    attrs: {
      line: {
        stroke: '#8c8c8c',
        strokeWidth: 1,
        targetMarker: { name: 'classic', size: 8 },
      },
    },
    router: { name: 'metro' },
    connector: { name: 'smooth' },
    zIndex: 1,
  };
}

function getNodeData(node: Node): NodeData | null {
  const data = node.getData() as any;
  if (!data || typeof data !== 'object') return null;
  if (data.kind === 'ui' && data.nodeType === 'input') return data as NodeData;
  if (data.kind === 'step' && data.step && data.nodeType) return data as NodeData;
  return null;
}

function getStepFromNode(node: Node): Step | null {
  const data = getNodeData(node) as any;
  if (data?.kind !== 'step') return null;
  return data.step as Step;
}

function getVarTypeFromPort(node: Node, portId?: string | null): VarType {
  if (!portId) return 'any';
  const meta: any = node.getPort(portId);
  return (meta?.args?.varType as VarType) || 'any';
}

function buildVarPorts(vars: VarDef[], prefix: string, group: 'left' | 'right') {
  return vars
    .filter((v) => v && typeof v === 'object' && v.key && v.name)
    .map((v) => ({
      id: `${prefix}:${v.key}`,
      group,
      args: { varType: v.type, varKey: v.key, varName: v.name, varGroup: prefix },
      attrs: {
        label: { text: v.name, fill: '#1f1f1f', fontSize: 10 },
      },
      label: {
        markup: [{ tagName: 'text', selector: 'label' }],
        position: group === 'right' ? 'right' : 'left',
      },
    }));
}

export function syncNodePorts(node: Node) {
  // React 自定义节点的端口由组件内部管理，跳过
  const selfManagedShapes = ['flow-if', 'flow-evaluate', 'flow-database', 'flow-httpRequest', 'flow-request'];
  if (selfManagedShapes.includes(node.shape)) return;

  const data = getNodeData(node) as any;
  if (!data) return;

  const groups = {
    left: {
      position: 'left',
      attrs: { circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' } },
    },
    right: {
      position: 'right',
      attrs: { circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' } },
    },
    manual: {
      position: 'absolute',
      attrs: { circle: { r: 4, magnet: true, stroke: '#1677ff', strokeWidth: 1, fill: '#fff' } },
    },
  } as any;

  const items: any[] = [];

  if (data.kind === 'ui' && data.nodeType === 'input') {
    // Start 节点只有输出
    if (node.shape === 'flow-start-card') {
      items.push({
        id: 'out',
        group: 'manual',
        args: { x: 260, y: 22, portType: 'control' },
        attrs: { label: { text: '' } }
      });

      items.push({
        id: 'out:headers-object',
        group: 'manual',
        args: { x: 260, y: 68 },
        attrs: { label: { text: '' } }
      });
      items.push({
        id: 'out:params-object',
        group: 'manual',
        args: { x: 260, y: 88 },
        attrs: { label: { text: '' } }
      });
      const method = (data.start?.method || 'GET').toUpperCase();
      if (method === 'POST' || method === 'PUT' || method === 'PATCH') {
        items.push({
          id: 'out:body-object',
          group: 'manual',
          args: { x: 260, y: 108 },
          attrs: { label: { text: '' } }
        });
      }
    } else {
      items.push({ id: 'out', group: 'right', args: { portType: 'control' } });
    }

    node.prop('ports', { groups, items });
    return;
  }

  if (data.kind === 'step') {
    const step = data.step as any;
    const type = data.nodeType as StepType;


    if (type === 'call') {
      const args = Array.isArray(step.args) ? step.args : [];
      args.forEach((_: any, idx: number) => {
        items.push({
          id: `in:arg:${idx}`,
          group: 'left',
          args: { varType: 'any', varKey: `${idx}`, varName: `arg${idx + 1}` },
          attrs: { label: { text: `arg${idx + 1}`, fill: '#1f1f1f', fontSize: 10 } },
          label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'left' },
        });
      });
      items.push({
        id: `out:output`,
        group: 'right',
        args: { varType: 'any', varKey: 'output', varName: step.output || 'output' },
        attrs: { label: { text: step.output || 'output', fill: '#1f1f1f', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
      });
    } else if (type === 'api') {
      const args = step.args && typeof step.args === 'object' && !Array.isArray(step.args) ? step.args : {};
      Object.keys(args).forEach((k) => {
        items.push({
          id: `in:arg:${k}`,
          group: 'left',
          args: { varType: 'any', varKey: k, varName: k },
          attrs: { label: { text: k, fill: '#1f1f1f', fontSize: 10 } },
          label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'left' },
        });
      });
      items.push({
        id: `out:output`,
        group: 'right',
        args: { varType: 'any', varKey: 'output', varName: step.output || 'output' },
        attrs: { label: { text: step.output || 'output', fill: '#1f1f1f', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
      });
    } else if (type === 'if') {
      items.push({
        id: `out:true`,
        group: 'right',
        args: { portType: 'control' },
        attrs: { label: { text: 'True', fill: '#52c41a', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
      });
      items.push({
        id: `out:false`,
        group: 'right',
        args: { portType: 'control' },
        attrs: { label: { text: 'False', fill: '#ff4d4f', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
      });
    } else if (type === 'switch') {
      const cases = step.cases || [];
      cases.forEach((c: string) => {
        items.push({
          id: `out:case_${c}`,
          group: 'right',
          args: { portType: 'control' },
          attrs: { label: { text: c, fill: '#1f1f1f', fontSize: 10 } },
          label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
        });
      });
      items.push({
        id: `out:default`,
        group: 'right',
        args: { portType: 'control' },
        attrs: { label: { text: 'Default', fill: '#8c8c8c', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
      });
    } else if (type === 'condition') {
      items.splice(0, items.length); // Remove generic ports
      const vars = Array.isArray(step.variables) ? step.variables : [];
      // 变量输入端口
      vars.forEach((v: any, idx: number) => {
        items.push({
          id: `in:var:${v.id}`,
          group: 'manual',
          args: { x: 0, y: 40 + idx * 40 + 25 },
          attrs: { label: { text: '', fill: '#1f1f1f', fontSize: 10 } },
        });
      });
      // Data 输入端口
      const size = node.getSize();
      const currentH = size ? size.height : 300;
      const currentW = size ? size.width : 320;
      const footerY = currentH - 80;

      items.push({
        id: `in:data`,
        group: 'manual',
        args: { x: 0, y: footerY + 40 },
        attrs: { label: { text: '', fill: '#1f1f1f', fontSize: 10 } },
      });
      // THEN / ELSE 输出端口
      items.push({
        id: `out:then`,
        group: 'manual',
        args: { x: currentW, y: footerY + 20, portType: 'control' },
        attrs: { label: { text: '' } },
      });
      items.push({
        id: `out:else`,
        group: 'manual',
        args: { x: currentW, y: footerY + 60, portType: 'control' },
        attrs: { label: { text: '' } },
      });
    } else if (type === 'return') {
      const mode = step.mode || 'response';
      if (mode === 'response') {
        items.push({
          id: `in:status`,
          group: 'left',
          args: { varType: 'number', varKey: 'status', varName: 'statusCode' },
          attrs: { label: { text: 'statusCode', fill: '#1f1f1f', fontSize: 10 } },
          label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'left' },
        });
        items.push(...buildVarPorts(step.response?.headers || [], 'in:header', 'left'));
        items.push(...buildVarPorts(step.response?.body || [], 'in:body', 'left'));
      } else {
        items.push({
          id: `in:value`,
          group: 'left',
          args: { varType: 'any', varKey: 'value', varName: 'value' },
          attrs: { label: { text: 'value', fill: '#1f1f1f', fontSize: 10 } },
          label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'left' },
        });
      }
    } else {
      items.push({
        id: `in:data`,
        group: 'left',
        args: { varType: 'any', varKey: 'data', varName: 'data' },
        attrs: { label: { text: 'data', fill: '#1f1f1f', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'left' },
      });
      items.push({
        id: `out:data`,
        group: 'right',
        args: { varType: 'any', varKey: 'data', varName: 'data' },
        attrs: { label: { text: 'data', fill: '#1f1f1f', fontSize: 10 } },
        label: { markup: [{ tagName: 'text', selector: 'label' }], position: 'right' },
      });
    }

    node.prop('ports', { groups, items });
  }
}

export function updateNodeLabel(node: Node) {
  const data = getNodeData(node);
  if (!data) return;
  if (data.kind === 'ui' && data.nodeType === 'input') {
    const d = data as any;
    const method = d?.start?.method || 'GET';
    if (node.shape === 'flow-start-card') {
      // React Node handles rendering itself via data listener
      // We only keep the debug log if needed
      try {
        const w: any = typeof window !== 'undefined' ? (window as any) : {};
        if (w.__SDS_FLOW_DEBUG_START__ === true || String(`${window?.location?.search || ''}${window?.location?.hash || ''}`).includes('debugStart=1')) {
          // eslint-disable-next-line no-console
          console.error('[StartNode][updateNodeLabel]', {
            nodeId: node.id,
            name: d?.name || 'Request',
            method,
          });
        }
      } catch { }
    } else if (node.shape === 'rect') {
      node.attr('label/text', `${d?.name || 'Request'}\n${method}`);
    }
    return;
  }
  if (data.kind !== 'step') return;
  if (node.shape !== 'rect') return;
  const def = getNodeDefinition(data.nodeType);
  const label = (data as any).step?.name || def?.getDisplayLabel(data) || `${data.nodeType}#${(data as any).step?.id}`;
  node.attr('label/text', label);
}

function isControlEdge(edge: Edge) {
  const data: any = edge.getData();
  if (data?.kind) return data.kind === 'control';
  const sp = (edge.getSource() as any)?.port;
  const tp = (edge.getTarget() as any)?.port;
  return (sp === 'out' || sp === 'out:then' || sp === 'out:else') && tp === 'in';
}

function sortNodesByGraph(graph: Graph, nodes: Node[]) {
  const edges = graph.getEdges().filter(isControlEdge);
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const indeg = new Map<string, number>();
  const next = new Map<string, string[]>();

  nodes.forEach((n) => indeg.set(n.id, 0));
  edges.forEach((e) => {
    const source = e.getSourceCellId();
    const target = e.getTargetCellId();
    if (!source || !target) return;
    if (!byId.has(source) || !byId.has(target)) return;
    indeg.set(target, (indeg.get(target) || 0) + 1);
    next.set(source, [...(next.get(source) || []), target]);
  });

  const queue: string[] = [];
  indeg.forEach((v, k) => {
    if (v === 0) queue.push(k);
  });
  queue.sort((a, b) => (byId.get(a)?.position().y ?? 0) - (byId.get(b)?.position().y ?? 0));

  const ordered: string[] = [];
  while (queue.length) {
    const id = queue.shift()!;
    ordered.push(id);
    const tos = next.get(id) || [];
    tos.forEach((to) => {
      const v = (indeg.get(to) || 0) - 1;
      indeg.set(to, v);
      if (v === 0) queue.push(to);
    });
    queue.sort((a, b) => (byId.get(a)?.position().y ?? 0) - (byId.get(b)?.position().y ?? 0));
  }

  if (ordered.length !== nodes.length) {
    return nodes
      .slice()
      .sort((a, b) => (a.position().y - b.position().y) || (a.position().x - b.position().x))
      .map((n) => n.id);
  }
  return ordered;
}

export type ValidationResult = {
  ok: boolean;
  message: string | null;
};

export function validateGraph(graph: Graph): ValidationResult {
  const nodes = graph.getNodes();
  const inputNodes = nodes.filter((n) => (getNodeData(n) as any)?.kind === 'ui' && (getNodeData(n) as any)?.nodeType === 'input');
  if (inputNodes.length > 1) {
    return { ok: false, message: '页面中只能有一个“输入”节点' };
  }

  const stepNodes = nodes.filter((n) => (getNodeData(n) as any)?.kind === 'step');
  const returnNodes = stepNodes.filter((n) => (getStepFromNode(n) as any)?.type === 'return');
  if (returnNodes.length > 1) {
    return { ok: false, message: '页面中只能有一个“返回”节点' };
  }

  if (returnNodes.length === 1) {
    const returnNode = returnNodes[0];
    const outgoing = (graph.getOutgoingEdges(returnNode) || []).filter(isControlEdge);
    if (outgoing.length > 0) {
      return { ok: false, message: '“返回”节点不能有后继连线，请删除其出边' };
    }
  }

  const orderedIds = sortNodesByGraph(graph, stepNodes);
  const orderedSteps = orderedIds.map((id) => stepNodes.find((n) => n.id === id)).filter(Boolean) as Node[];
  const orderedReturnIndex = orderedSteps.findIndex((n) => (getStepFromNode(n) as any)?.type === 'return');
  if (orderedReturnIndex !== -1 && orderedReturnIndex !== orderedSteps.length - 1) {
    return { ok: false, message: '“返回”必须是最后一个步骤节点（请调整连线/位置）' };
  }

  return { ok: true, message: null };
}

export function exportGraphToFlowDefinition(graph: Graph, base: FlowDefinition): { flow: FlowDefinition; layout: UiGraphLayout } {
  const nodes = graph.getNodes();
  const edges = graph.getEdges();

  const layoutNodes: UiGraphLayout['nodes'] = {};
  nodes.forEach((n) => {
    const pos = n.position();
    const size = n.size();
    layoutNodes[n.id] = { x: pos.x, y: pos.y, width: size.width, height: size.height };
  });

  const layoutEdges: UiGraphLayout['edges'] = edges
    .map((e) => {
      const s = e.getSourceCellId();
      const t = e.getTargetCellId();
      if (!s || !t) return null;
      const source = e.getSource() as any;
      const target = e.getTarget() as any;
      const sourcePort = source?.port as string | undefined;
      const targetPort = target?.port as string | undefined;
      const data: any = e.getData();
      const kind: any = data?.kind || (sourcePort?.startsWith('out:') || targetPort?.startsWith('in:') ? 'mapping' : 'control');
      return { source: s, target: t, sourcePort, targetPort, kind };
    })
    .filter(Boolean) as UiGraphLayout['edges'];

  const stepNodes = nodes.filter((n) => (getNodeData(n) as any)?.kind === 'step');
  const orderedIds = sortNodesByGraph(graph, stepNodes);
  const stepById = new Map<string, Step>();
  stepNodes.forEach((n) => {
    const step = getStepFromNode(n);
    if (!step) return;
    stepById.set(n.id, { ...step, id: n.id } as Step);
  });

  const steps = orderedIds.map((id) => {
    const step = stepById.get(id);
    if (!step) return null;

    // 填充 next 映射 (Jointer)
    // 支持并行: 如果同一个端口连接多个节点,使用数组
    const nextMap: Record<string, string[]> = {};
    const outgoing = graph.getOutgoingEdges(id) || [];
    outgoing.forEach(edge => {
      const data: any = edge.getData();
      if (data?.kind === 'control') {
        const sourcePort = (edge.getSource() as any).port as string;
        const targetId = edge.getTargetCellId();
        if (targetId) {
          // 如果是 out:then 或 out:else,提取后缀作为 key
          const portKey = sourcePort.startsWith('out:') ? sourcePort.substring(4) : sourcePort;
          if (!nextMap[portKey]) {
            nextMap[portKey] = [];
          }
          nextMap[portKey].push(targetId);
        }
      }
    });

    // 转换为最终的 next 对象: 单个目标用字符串,多个目标用数组
    const next: Record<string, string | string[]> = {};
    Object.keys(nextMap).forEach(key => {
      const targets = nextMap[key];
      if (targets.length === 1) {
        next[key] = targets[0];
      } else if (targets.length > 1) {
        next[key] = targets;
      }
    });

    return { ...step, next };
  }).filter(Boolean) as Step[];

  const inputNode = nodes.find((n) => (getNodeData(n) as any)?.kind === 'ui' && (getNodeData(n) as any)?.nodeType === 'input');
  const uiStart = inputNode ? (getNodeData(inputNode) as any) : null;

  // 查找 startStepId: 从 inputNode 出发的 control 连线指向的节点
  let startStepId: string | undefined = undefined;
  if (inputNode) {
    const outgoing = graph.getOutgoingEdges(inputNode) || [];
    const firstEdge = outgoing.find(e => {
      const d: any = e.getData();
      return d?.kind === 'control' || (e.getSource() as any).port === 'out';
    });
    if (firstEdge) {
      startStepId = firstEdge.getTargetCellId();
    }
  }

  const args = base.args ? { ...base.args } : {};
  const ui = (args as any)[UI_LAYOUT_KEY] ? { ...(args as any)[UI_LAYOUT_KEY] } : {};
  if (uiStart) {
    ui.start = { name: uiStart.name, start: uiStart.start };
  }
  (args as any)[UI_LAYOUT_KEY] = ui;

  const flow: FlowDefinition = {
    id: base.id,
    version: base.version,
    startStepId,
    args,
    errors: base.errors,
    steps,
  };

  const layout: UiGraphLayout = { version: 1, nodes: layoutNodes, edges: layoutEdges };

  return { flow, layout };
}

export function importFlowDefinitionToGraph(graph: Graph, def: FlowDefinition) {
  graph.clearCells();

  const layout = readLayoutFromFlow(def);
  const steps = Array.isArray(def.steps) ? def.steps : [];
  const hasLayout = !!layout;

  const normalizedSteps = steps.map((s: any, index) => {
    const type = (s?.type as StepType) || 'call';
    const id = (s?.id as string | undefined) || `${type}_${index + 1}`;
    return { ...s, type, id } as Step;
  });

  const stepX = 160;
  const stepStartY = 120;
  const stepGapY = 120;
  const stepCenterX = stepX + 120;
  const inputX = stepCenterX - 140;
  const inputY = 40;

  const ui = (def.args as any)?.[UI_LAYOUT_KEY] || {};
  const startFromFlow = ui?.start || null;

  const hasInput = !!layout?.nodes?.input;
  const inputData: any = {
    kind: 'ui',
    nodeType: 'input',
    name: startFromFlow?.name || 'Request',
    start: startFromFlow?.start || { mode: 'request', method: 'GET', headers: [], params: [], body: [] },
  };
  const inputDef = getNodeDefinition('input');
  const inputNode = graph.createNode(
    inputDef?.buildNodeConfig(inputData, {
      x: hasInput ? layout!.nodes.input.x : inputX,
      y: hasInput ? layout!.nodes.input.y : inputY,
    }) || {
      id: 'input',
      shape: 'flow-start-card',
      x: hasInput ? layout!.nodes.input.x : inputX,
      y: hasInput ? layout!.nodes.input.y : inputY,
      data: inputData,
    },
  );
  graph.addNode(inputNode);
  updateNodeLabel(inputNode);
  syncNodePorts(inputNode);

  const stepNodes: Node[] = [];
  normalizedSteps.forEach((step, index) => {
    const nodeType = step.type as StepType;
    const nodeDef = getNodeDefinition(nodeType);
    if (!nodeDef) return;
    const data: NodeData = { kind: 'step', nodeType, step };
    const pos = layout?.nodes?.[step.id]
      ? { x: layout.nodes[step.id].x, y: layout.nodes[step.id].y }
      : hasLayout
        ? undefined
        : { x: stepX, y: stepStartY + index * stepGapY };
    const node = graph.createNode(nodeDef.buildNodeConfig(data, pos));
    stepNodes.push(node);
  });

  graph.addNodes(stepNodes);
  stepNodes.forEach((n) => {
    updateNodeLabel(n);
    syncNodePorts(n);
  });

  if (layout?.edges?.length) {
    layout.edges.forEach((e) => {
      if (!graph.getCellById(e.source) || !graph.getCellById(e.target)) return;
      const kind = e.kind || 'control';
      if (kind === 'control') {
        graph.addEdge({
          ...createDefaultEdge(e.source, e.target),
          source: { cell: e.source, port: e.sourcePort || 'out' },
          target: { cell: e.target, port: e.targetPort || 'in' },
          data: { kind: 'control' },
        });
      } else {
        graph.addEdge({
          shape: 'edge',
          source: { cell: e.source, port: e.sourcePort },
          target: { cell: e.target, port: e.targetPort },
          data: { kind: 'mapping' },
          attrs: {
            line: { stroke: '#ff7a45', strokeWidth: 1, targetMarker: { name: 'classic', size: 8 } },
          },
          router: { name: 'normal' },
        });
      }
    });
    return;
  }

  // 没有 layout,从 step.next 和 startStepId 构建连接
  const uiInput = graph.getCellById('input');

  // 从 startStepId 创建第一个连接
  if (uiInput && def.startStepId) {
    const startNode = graph.getCellById(def.startStepId);
    if (startNode) {
      graph.addEdge(createDefaultEdge('input', def.startStepId));
    }
  }

  // 从每个 step.next 创建连接(支持并行)
  normalizedSteps.forEach((step) => {
    if (!step.next) return;
    const sourceNode = graph.getCellById(step.id);
    if (!sourceNode) return;

    Object.keys(step.next).forEach(portKey => {
      const nextValue = step.next![portKey];
      // portKey 是 'out', 'true', 'false', 'case_XXX', 'default' 等
      // 需要转换为实际的 port ID
      const sourcePort = portKey === 'out' ? 'out' : `out:${portKey}`;

      // 支持单个目标或多个目标(并行)
      const targets = Array.isArray(nextValue) ? nextValue : [nextValue];

      targets.forEach(targetId => {
        const targetNode = graph.getCellById(targetId);
        if (targetNode) {
          graph.addEdge({
            ...createDefaultEdge(step.id, targetId),
            source: { cell: step.id, port: sourcePort },
            target: { cell: targetId, port: 'in' },
            data: { kind: 'control' },
          });
        }
      });
    });
  });
}

// ============================================================================
// node-registry/index.ts
// 节点注册系统入口 —— 统一导出 + 初始化
//
// 设计原则：
//   - 节点【数据】（label/color/ports/defaults 等）在模块导入时立即注册，
//     这样 DslPalette 初次渲染时面板就能看到节点列表。
//   - X6【形状】注册（Graph.registerNode / register）推迟到 initNodeRegistry()，
//     必须在 Graph 实例化之前调用，确保 X6 内部状态就绪。
// ============================================================================

// ── 核心类型导出 ──
export type {
    NodeRegistration,
    PropertyEditorProps,
    ShapeRegistration,
    NodeDefaults,
    ImportConfig,
    PortMode,
    PortGroupName,
    LabelBuilder,
} from './types';

// ── 注册表 API 导出 ──
export {
    registerNode,
    registerNodes,
    registerAllShapes,
    getNodeRegistration,
    getAllRegistrations,
    getRegistrationsByCategory,
    getNodeColor,
    getNodeTagColor,
    hasInputsField,
    getDefaultPorts,
    getDefaultNodeData,
    getNodeSize,
    buildNodeLabel,
    getPropertyEditor,
    inferPortGroup,
    getPortLabel,
    getPortLabelColor,
    PORT_GROUPS,
} from './registry';

// ── 节点模块导入 ──

import { evaluateNodeRegistration } from './nodes/evaluate';
import { ifNodeRegistration } from './nodes/if-node';
import { switchNodeRegistration } from './nodes/switch-node';
import { serviceCallNodeRegistration } from './nodes/service-call';
import { httpRequestNodeRegistration } from './nodes/http-request';
import { forNodeRegistration } from './nodes/for-node';          // Scatter-Gather For
import { recordNodeRegistration } from './nodes/record';
import { responseNodeRegistration } from './nodes/response';
import { requestNodeRegistration } from './nodes/request';
import { templateNodeRegistration } from './nodes/template';
import { collectNodeReactRegistration } from './nodes/collect-node'; // Scatter-Gather Collect (React)
import { databaseNodeRegistration } from './nodes/database';
import { systemVarNodeRegistration } from './nodes/system-var';
import { systemMethodNodeRegistration } from './nodes/system-method';

import { registerNodes, registerAllShapes } from './registry';

// ── 所有内置节点注册列表 ──
const BUILTIN_NODES = [
    evaluateNodeRegistration,
    ifNodeRegistration,
    switchNodeRegistration,
    serviceCallNodeRegistration,
    httpRequestNodeRegistration,
    forNodeRegistration,           // Scatter-Gather: 分发台
    recordNodeRegistration,
    responseNodeRegistration,
    requestNodeRegistration,
    templateNodeRegistration,
    collectNodeReactRegistration,  // Scatter-Gather: 汇聚屏障
    databaseNodeRegistration,
    systemVarNodeRegistration,
    systemMethodNodeRegistration,
];

// ─────────────────────────────────────────────────────────────────────────────
// 【第一阶段】模块导入时立即注册节点数据（无 X6 副作用）
// DslPalette 初次渲染时依赖此数据填充面板列表
// ─────────────────────────────────────────────────────────────────────────────
registerNodes(BUILTIN_NODES);

// ─────────────────────────────────────────────────────────────────────────────
// 【第二阶段】X6 形状注册，需在 Graph 实例化之前调用
// 由 FlowEditor 在创建 Graph 前显式调用 initNodeRegistry()
// ─────────────────────────────────────────────────────────────────────────────
let _shapesReady = false;

/**
 * 注册 X6 自定义形状（SVG / React Shape）。
 * 必须在 `new Graph(...)` 之前调用，且保证幂等。
 *
 * 节点数据在模块导入时已自动注册，此函数只处理 X6 形状层面。
 */
export function initNodeRegistry(): void {
    if (_shapesReady) return;
    _shapesReady = true;
    registerAllShapes();
}

// ============================================================================
// registerCustomNodes.ts
// V3.2 兼容层 —— 委托给 node-registry/index.ts 统一初始化
//
// 保留此文件以维持外部导入兼容性（FlowEditor 已更新到 initNodeRegistry，
// 但如有旧引用，此文件仍可安全调用）
// ============================================================================

import { initNodeRegistry } from './node-registry';

/**
 * 注册所有 V3.x 自定义节点形状到 AntV X6。
 * @deprecated 请改用 initNodeRegistry() from './node-registry'
 */
export function registerCustomNodes(): void {
    initNodeRegistry();
}

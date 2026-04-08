// ============================================================================
// node-registry/registry.ts
// 节点注册中心 —— 管理所有节点类型的注册与查询
// ============================================================================

import { Graph } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import type { DslNodeType, DslPort } from '../types';
import type { NodeRegistration, PortGroupName } from './types';

// ── 通用端口分组配置 (给 SVG 注册节点使用) ──
export const PORT_GROUPS: Record<string, any> = {
    // 1. 右侧输出端口（实心）
    'out-solid': {
        position: 'right',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L 4,-8 A 4,4 0 0,1 4,8 L 0,8 Z', fill: '#6b7280', stroke: 'none', magnet: true },
        },
    },
    // 2. 右侧输出端口（中空/实线边框）
    'out-hollow': {
        position: 'right',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L 4,-8 A 4,4 0 0,1 4,8 L 0,8 Z', fill: '#ffffff', stroke: '#6b7280', strokeWidth: 1.5, magnet: true },
        },
    },
    // 3. 左侧输入端口（实心）
    'in-solid': {
        position: 'left',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L -4,-8 A 4,4 0 0,0 -4,8 L 0,8 Z', fill: '#6b7280', stroke: 'none', magnet: true },
        },
    },
    // 4. 左侧输入端口（中空）
    'in-hollow': {
        position: 'left',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L -4,-8 A 4,4 0 0,0 -4,8 L 0,8 Z', fill: '#ffffff', stroke: '#6b7280', strokeWidth: 1.5, magnet: true },
        },
    },

    // ── 针对需要精确绝对定位 (x, y) 的节点的兼容分组 ──
    'absolute-out-solid': {
        position: 'absolute',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L 4,-8 A 4,4 0 0,1 4,8 L 0,8 Z', fill: '#6b7280', stroke: 'none', magnet: true },
        },
    },
    'absolute-out-hollow': {
        position: 'absolute',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L 4,-8 A 4,4 0 0,1 4,8 L 0,8 Z', fill: '#ffffff', stroke: '#6b7280', strokeWidth: 1.5, magnet: true },
        },
    },
    'absolute-in-solid': {
        position: 'absolute',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L -4,-8 A 4,4 0 0,0 -4,8 L 0,8 Z', fill: '#6b7280', stroke: 'none', magnet: true },
        },
    },
    'absolute-in-hollow': {
        position: 'absolute',
        markup: [{ tagName: 'path', selector: 'x6-port-body', className: 'x6-port-body' }],
        attrs: {
            '.x6-port-body': { d: 'M 0,-8 L -4,-8 A 4,4 0 0,0 -4,8 L 0,8 Z', fill: '#ffffff', stroke: '#6b7280', strokeWidth: 1.5, magnet: true },
        },
    },
};

// ── 注册表 ──
const _registry = new Map<DslNodeType, NodeRegistration>();
let _shapesRegistered = false;

// ============================================================================
// 注册 API
// ============================================================================

/**
 * 注册一个节点类型。可在应用初始化时调用。
 * 重复注册相同 type 会覆盖旧配置。
 */
export function registerNode(registration: NodeRegistration): void {
    _registry.set(registration.type, registration);
}

/**
 * 批量注册节点类型
 */
export function registerNodes(registrations: NodeRegistration[]): void {
    registrations.forEach(registerNode);
}

// ============================================================================
// 查询 API
// ============================================================================

/** 获取指定类型的注册信息 */
export function getNodeRegistration(type: DslNodeType): NodeRegistration | undefined {
    return _registry.get(type);
}

/** 获取所有已注册的节点类型 */
export function getAllRegistrations(): NodeRegistration[] {
    return Array.from(_registry.values());
}

/** 获取按 category 分组的注册信息 (用于面板) */
export function getRegistrationsByCategory(): Array<[string, NodeRegistration[]]> {
    const map = new Map<string, NodeRegistration[]>();
    _registry.forEach((reg) => {
        const list = map.get(reg.category) || [];
        list.push(reg);
        map.set(reg.category, list);
    });
    return Array.from(map.entries());
}

/** 获取节点颜色 */
export function getNodeColor(type: DslNodeType): string {
    return _registry.get(type)?.color || '#595959';
}

/** 获取节点标签颜色 (用于 Tag) */
export function getNodeTagColor(type: DslNodeType): string {
    return _registry.get(type)?.tagColor || 'default';
}

/** 节点类型是否有 inputs 配置 */
export function hasInputsField(type: DslNodeType): boolean {
    return _registry.get(type)?.hasInputs ?? false;
}

/** 获取节点默认端口 */
export function getDefaultPorts(type: DslNodeType): DslPort[] {
    return _registry.get(type)?.defaults.ports || [{ id: 'in' }, { id: 'out' }];
}

/** 获取节点默认数据 */
export function getDefaultNodeData(type: DslNodeType): Record<string, any> {
    return _registry.get(type)?.defaults.data || {};
}

/** 获取节点默认尺寸 */
export function getNodeSize(type: DslNodeType, ports: DslPort[]): { width: number; height: number } {
    const reg = _registry.get(type);
    if (!reg) return { width: 200, height: 60 };
    if (reg.defaults.dynamicSize) return reg.defaults.dynamicSize(ports);
    return reg.defaults.size;
}

/** 构建节点标签 */
export function buildNodeLabel(type: DslNodeType, data: Record<string, any>): string {
    const reg = _registry.get(type);
    if (!reg) return type.charAt(0).toUpperCase() + type.slice(1);
    return reg.buildLabel(data);
}

/** 获取属性面板编辑器组件 */
export function getPropertyEditor(type: DslNodeType): React.ComponentType<any> | undefined {
    return _registry.get(type)?.PropertyEditor;
}

// ============================================================================
// X6 形状注册 (只调用一次)
// ============================================================================

/**
 * 将所有已注册的节点形状注册到 AntV X6。
 * 保证只注册一次（幂等）。
 */
export function registerAllShapes(): void {
    if (_shapesRegistered) return;
    _shapesRegistered = true;

    // ── 注入全局端口动画样式 ──
    if (typeof document !== 'undefined') {
        const styleId = 'x6-port-dynamic-styles';
        if (!document.getElementById(styleId)) {
            const style = document.createElement('style');
            style.id = styleId;
            // 端口默认缩放宽度 (0.35倍大概 1.4px 宽，实现扁平卡片边缘) 
            style.innerHTML = `
                .x6-port-body {
                    transition: transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1), fill 0.2s ease, stroke-opacity 0.2s ease !important;
                    transform-origin: 0 0;
                    /* 保持边框在缩放时不发生形变压缩 */
                    vector-effect: non-scaling-stroke;
                }
                .x6-port:not(:hover) .x6-port-body {
                    transform: scaleX(0.35);
                }
                .x6-port:hover .x6-port-body {
                    transform: scaleX(1);
                }
                .x6-port:not(:hover) .x6-port-body[stroke]:not([stroke="none"]) {
                    stroke-opacity: 0.45;
                }
                .x6-port:hover .x6-port-body[stroke]:not([stroke="none"]) {
                    stroke-opacity: 1;
                }
            `;
            document.head.appendChild(style);
        }
    }

    _registry.forEach((reg) => {
        const { shape } = reg;

        if (shape.kind === 'svg' && shape.svgConfig) {
            Graph.registerNode(
                shape.shapeName,
                {
                    ...shape.svgConfig,
                    ports: {
                        groups: PORT_GROUPS,
                        ...(shape.svgConfig.ports || {}),
                    },
                },
                true,
            );
        } else if (shape.kind === 'react' && shape.component) {
            register({
                shape: shape.shapeName,
                width: reg.defaults.size.width,
                height: reg.defaults.size.height,
                // x6-react-shape 期望 ComponentType<{ node: Node; graph: Graph }>,
                // 实际组件只用到 node，使用 as any 绕过类型检查
                component: shape.component as any,
                ports: {
                    groups: PORT_GROUPS,
                    ...(shape.reactPorts || {})
                },
            });
        }
    });
}

// ============================================================================
// 端口工具函数
// ============================================================================

/** 根据端口 ID 推断端口组 */
export function inferPortGroup(portId: string): PortGroupName {
    const map: Record<string, PortGroupName> = {
        in: 'left',
        'in:headers': 'left',
        'in:body': 'left',
        // Scatter-Gather 控制流端口
        start: 'left',      // For 可选控制流输入
        // Scatter-Gather 数据流端口
        item: 'right',      // For 输出 / Collect 输入
        list: 'right',      // Collect 输出
        finish: 'right',    // Collect 控制流输出
        // 其他
        out: 'right',
        true: 'right',
        false: 'right',
        done: 'right',
        default: 'right',
        headers: 'right',
        params: 'right',
        body: 'right',
    };
    return map[portId] || 'right';
}

/** 获取端口显示标签 */
export function getPortLabel(portId: string): string {
    const labels: Record<string, string> = {
        in: 'IN', out: 'OUT', true: 'True', false: 'False',
        item: 'Item', done: 'Done', default: 'Default',
        headers: 'Headers', params: 'Params', body: 'Body',
        list: 'List',
        start: 'Start',   // For 控制流输入
        finish: 'Finish',  // Collect 控制流输出
        'in:headers': 'Headers', 'in:body': 'Body',
    };
    if (portId.startsWith('case_')) return portId.substring(5);
    return labels[portId] || portId;
}

/** 获取端口标签颜色 */
export function getPortLabelColor(portId: string): string {
    if (portId === 'true' || portId === 'item') return '#52c41a';
    if (portId === 'false') return '#ff4d4f';
    if (portId === 'done' || portId === 'default') return '#8c8c8c';
    if (portId === 'start' || portId === 'finish') return '#8b5cf6'; // 控制流童紫色
    return '#1f1f1f';
}

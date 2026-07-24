// ============================================================================
// node-registry/registry.ts
// 节点注册中心 —— 管理所有节点类型的注册与查询
// ============================================================================

import { Graph } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import type { DslNodeType, DslPort } from '../types';
import type { NodeRegistration, PortGroupName } from './types';

// ── 通用端口分组配置 (给 SVG 注册节点使用) ──
/** Postman 向：边缘小圆磁吸（实心=已占用语义，空心=可选出口） */
const PORT_R = 4.5;
const portCircleMarkup = () => [
    { tagName: 'circle', selector: 'circle', className: 'x6-port-body' },
];
const portSolidAttrs = {
    circle: {
        r: PORT_R,
        magnet: true,
        fill: '#64748b',
        stroke: '#ffffff',
        strokeWidth: 2,
    },
};
const portHollowAttrs = {
    circle: {
        r: PORT_R,
        magnet: true,
        fill: '#ffffff',
        stroke: '#cbd5e1',
        strokeWidth: 2,
    },
};

export const PORT_GROUPS: Record<string, any> = {
    'out-solid': {
        position: 'right',
        markup: portCircleMarkup(),
        attrs: portSolidAttrs,
    },
    'out-hollow': {
        position: 'right',
        markup: portCircleMarkup(),
        attrs: portHollowAttrs,
    },
    'in-solid': {
        position: 'left',
        markup: portCircleMarkup(),
        attrs: portSolidAttrs,
    },
    'in-hollow': {
        position: 'left',
        markup: portCircleMarkup(),
        attrs: portHollowAttrs,
    },
    'absolute-out-solid': {
        position: 'absolute',
        markup: portCircleMarkup(),
        attrs: portSolidAttrs,
    },
    'absolute-out-hollow': {
        position: 'absolute',
        markup: portCircleMarkup(),
        attrs: portHollowAttrs,
    },
    'absolute-in-solid': {
        position: 'absolute',
        markup: portCircleMarkup(),
        attrs: portSolidAttrs,
    },
    'absolute-in-hollow': {
        position: 'absolute',
        markup: portCircleMarkup(),
        attrs: portHollowAttrs,
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

/** 获取按 category 分组的注册信息 (用于面板；默认排除 hidden) */
export function getRegistrationsByCategory(options?: { includeHidden?: boolean }): Array<[string, NodeRegistration[]]> {
    const includeHidden = options?.includeHidden === true;
    const map = new Map<string, NodeRegistration[]>();
    _registry.forEach((reg) => {
        if (!includeHidden && reg.hidden) return;
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
 * 默认只注册一次；force=true 时强制覆盖（用于 HMR / 修复端口默认值后热更新）。
 */
export function registerAllShapes(force = false): void {
    if (_shapesRegistered && !force) return;

    // ── 注入全局端口样式（小圆磁吸；始终更新以便 HMR）──
    if (typeof document !== 'undefined') {
        const styleId = 'x6-port-dynamic-styles';
        let style = document.getElementById(styleId) as HTMLStyleElement | null;
        if (!style) {
            style = document.createElement('style');
            style.id = styleId;
            document.head.appendChild(style);
        }
        style.innerHTML = `
            .x6-port-body {
                transition: transform 0.18s cubic-bezier(0.2, 0.8, 0.2, 1), fill 0.15s ease, stroke 0.15s ease, opacity 0.15s ease, filter 0.15s ease !important;
                transform-box: fill-box;
                transform-origin: center;
                vector-effect: non-scaling-stroke;
                cursor: crosshair;
                opacity: 0.9;
            }
            /* 选中节点：端口放大并完全实心，强化「可连线」感 */
            .x6-node.yf-node-selected .x6-port-body {
                opacity: 1;
                transform: scale(1.18);
            }
            /* hover 端口：进一步放大 + 投影（优先级高于选中态） */
            .x6-port:hover .x6-port-body {
                opacity: 1;
                transform: scale(1.55);
                filter: drop-shadow(0 1px 2px rgba(15, 23, 42, 0.28));
            }
            /* 右下角缩放抓握纹：默认淡、hover 加深，更「丝滑」 */
            .yf-resize-handle {
                opacity: 0.35;
                transition: opacity 0.15s ease;
            }
            .yf-resize-handle:hover {
                opacity: 0.85;
            }
        `;
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
            // 只注册 port groups，不把 reactPorts.items 写进形状默认值。
            // 否则 import / addNode 再传同名 ports.items 时，X6 会合并出
            // "Duplicated port id"（如 out / in / in:payload）。
            // 实例端口一律由 adapter.buildPortItems / 组件 useEffect 提供。
            register({
                shape: shape.shapeName,
                width: reg.defaults.size.width,
                height: reg.defaults.size.height,
                // x6-react-shape 期望 ComponentType<{ node: Node; graph: Graph }>,
                // 实际组件只用到 node，使用 as any 绕过类型检查
                component: shape.component as any,
                ports: {
                    groups: PORT_GROUPS,
                    items: [],
                },
            });
        }
    });
    _shapesRegistered = true;
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

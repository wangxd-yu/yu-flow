// ============================================================================
// node-registry/types.ts
// 节点注册系统核心类型定义 —— 每个节点类型的 Single Module Interface
// ============================================================================

import type React from 'react';
import type { Node } from '@antv/x6';
import type { DslNodeType, DslPort } from '../types';

// ── 端口分组 ─────────────────────────────────────────────────
export type PortGroupName = 'left' | 'right' | 'top' | 'bottom' | 'manual'
    | 'data'    // Scatter-Gather 数据流端口组（圆形）
    | 'control'; // Scatter-Gather 控制流端口组（三角形）


// ── 端口定位模式 ─────────────────────────────────────────────
export type PortMode = 'standard' | 'manual';

// ── 导入端口构建上下文 ─────────────────────────────────────────
export interface PortBuildContext {
    ports: DslPort[];
    nodeType: DslNodeType;
    /** 当 PortMode 为 manual 时，构建 portItems 的回调 */
}

// ── X6 节点形状注册配置 ─────────────────────────────────────────
export interface ShapeRegistration {
    /** X6 shape 名称, e.g. 'flow-start' */
    shapeName: string;
    /** 'svg' = Graph.registerNode, 'react' = register(@antv/x6-react-shape) */
    kind: 'svg' | 'react';
    /** SVG 形状配置 (kind=svg 时使用) */
    svgConfig?: Record<string, any>;
    /** React 组件 (kind=react 时使用) */
    component?: React.ComponentType<{ node: Node }>;
    /** React 形状注册时的端口配置 (kind=react 时使用) */
    reactPorts?: Record<string, any>;
}

// ── 节点默认数据 ─────────────────────────────────────────────
export interface NodeDefaults {
    /** 默认端口列表 */
    ports: DslPort[];
    /** 默认业务数据 */
    data: Record<string, any>;
    /** 默认尺寸 */
    size: { width: number; height: number };
    /** 尺寸是否需要根据端口数量动态计算 */
    dynamicSize?: (ports: DslPort[]) => { width: number; height: number };
}

// ── 导入配置 (DSL -> X6) ─────────────────────────────────────
export interface ImportConfig {
    /** 端口组装模式: standard = 用 left/right 组 + 文字标签, manual = 自定义绝对定位 */
    portMode: PortMode;
    /** 自定义端口构建 (portMode=manual 时必须提供) */
    buildPortItems?: (ports: DslPort[]) => any[];
    /** 自定义 attrs 构建 */
    buildAttrs?: (color: string, label: string) => Record<string, any>;
}

// ── 属性面板编辑器 ─────────────────────────────────────────────
export type PropertyEditorProps = {
    node: Node;
    data: Record<string, any>;
    onChange: (changes: Record<string, any>) => void;
};

// ── 标签构建 ─────────────────────────────────────────────────
export type LabelBuilder = (data: Record<string, any>) => string;

// ============================================================================
// NodeRegistration —— 节点注册的核心接口
// 每个节点类型模块需要导出一个符合此接口的对象
// ============================================================================

export interface NodeRegistration {
    /** 节点类型 (唯一标识) */
    type: DslNodeType;
    /** 显示名称 */
    label: string;
    /** 分类 (用于面板分组) */
    category: string;
    /** 主色调 */
    color: string;
    /** 标签颜色 (用于属性面板 Tag) */
    tagColor: string;
    /** 组件面板中的排序权重 (越大越靠前，默认 0) */
    sortOrder?: number;
    /** 是否单例 (画布中只允许一个) */
    singleton?: boolean;
    /** 是否有 inputs 配置区 (通用 JSONPath 映射) */
    hasInputs: boolean;

    /** X6 形状注册 */
    shape: ShapeRegistration;
    /** 节点默认值 */
    defaults: NodeDefaults;
    /** DSL 导入配置 */
    importConfig: ImportConfig;
    /** 标签文本构建 */
    buildLabel: LabelBuilder;

    /** 属性面板编辑器组件 (右侧面板) */
    PropertyEditor?: React.ComponentType<PropertyEditorProps>;
}

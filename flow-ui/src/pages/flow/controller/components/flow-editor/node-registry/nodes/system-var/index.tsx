// ============================================================================
// node-registry/nodes/system-var/index.tsx
// SystemVar (系统变量) 节点 —— 供全局变量调用的源节点
// ============================================================================

import React from 'react';
import type { NodeRegistration } from '../../types';
import { SystemVarNodeComponent, SYSTEM_VAR_LAYOUT } from './SystemVarNodeComponent';

// ── 注册配置 ──
export const systemVarNodeRegistration: NodeRegistration = {
    type: 'systemVar',
    label: '系统变量 (SystemVar)',
    category: '数据节点',
    color: '#34d399',
    tagColor: 'green',
    hasInputs: false,

    shape: {
        shapeName: 'flow-system-var',
        kind: 'react',
        component: SystemVarNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: SYSTEM_VAR_LAYOUT.width, y: SYSTEM_VAR_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out', group: 'absolute-out-solid' }],
        // 🔒 安全：不存储 expression，仅保留 variableCode 作为后端执行凭证
        data: { variableCode: '' },
        size: { width: SYSTEM_VAR_LAYOUT.width, height: SYSTEM_VAR_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports) => {
            return ports.filter(p => p.id === 'out').map(p => ({
                id: p.id,
                group: 'absolute-out-solid',
                args: { x: SYSTEM_VAR_LAYOUT.width, y: SYSTEM_VAR_LAYOUT.outPortY, dx: 0 },
            }));
        },
        buildAttrs: () => ({
            // Pill 节点使用内部自己的样式画边框，外层透明
            body: { stroke: 'transparent', fill: 'transparent' },
        }),
    },

    buildLabel: (data) =>
        data.variableCode ? `{${String(data.variableCode)}}` : 'SystemVar',
};

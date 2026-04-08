// ============================================================================
// node-registry/nodes/system-method/index.tsx
// SystemMethod (系统方法调用) 节点注册模块  —  V3
// ============================================================================

import type { NodeRegistration } from '../../types';
import { SystemMethodNodeComponent, SYSTEM_METHOD_LAYOUT } from './SystemMethodNodeComponent';

// ── 注册配置 ──
export const systemMethodNodeRegistration: NodeRegistration = {
    type: 'systemMethod',
    label: '系统方法',
    category: '调用节点',
    color: '#a855f7',
    tagColor: 'purple',
    hasInputs: true,

    shape: {
        shapeName: 'flow-system-method',
        kind: 'react',
        component: SystemMethodNodeComponent,
        // 初始仅定义 out 端口；in:arg:* 端口由组件根据选择的方法动态生成
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    // outPortY 现在指向底部表达式行的中心
                    args: { x: SYSTEM_METHOD_LAYOUT.width, y: SYSTEM_METHOD_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out' }],  // 数据结果输出端口；in:arg:* 由组件动态生成
        // 🔒 安全：不存储 expression，仅保留 methodCode + inputs 作为后端执行凭证
        data: { methodCode: '', inputs: {} },
        // baseHeight = Header(40) + Footer(40) = 80px（无参数时）
        size: { width: SYSTEM_METHOD_LAYOUT.width, height: SYSTEM_METHOD_LAYOUT.baseHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports) => {
            return ports.map(p => {
                // 端口 ID 语义：
                //   out → X=NODE_W（左边手）
                //   in:arg:*   → X=0（右边手），占位值。精确 Y 由组件 useEffect 重建
                const isOut = p.id === 'out';
                return {
                    id: p.id,
                    group: isOut ? 'absolute-out-solid' : 'absolute-in-solid',
                    // 精确坐标由组件挂载后的 useEffect 重建；此处给占位值
                    args: isOut
                        ? { x: SYSTEM_METHOD_LAYOUT.width, y: SYSTEM_METHOD_LAYOUT.outPortY, dx: 0 }
                        : { x: 0, y: SYSTEM_METHOD_LAYOUT.outPortY, dx: 0 },
                };
            });
        },
        buildAttrs: () => ({
            body: { stroke: 'transparent', fill: 'transparent' },
        }),
    },

    buildLabel: (data) =>
        data.methodCode ? `[{${data.methodCode}}] SystemMethod` : 'SystemMethod',
};

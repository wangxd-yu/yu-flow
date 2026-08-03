// ============================================================================
// json-map/index.tsx — JsonMap 节点注册
// ============================================================================

import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import {
    JsonMapNodeComponent,
    JSON_MAP_LAYOUT,
    JSON_MAP_COLOR,
} from './JsonMapNodeComponent';
import { PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';

const buildPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in') continue;
        seen.add(p.id);
        if (p.id === 'out') {
            items.push({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: JSON_MAP_LAYOUT.width, y: JSON_MAP_LAYOUT.outPortY, dx: 0 },
            });
        } else if (p.id === 'in:payload') {
            items.push({
                id: 'in:payload',
                group: 'absolute-in-solid',
                args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
            });
        }
    }
    if (!seen.has('in:payload')) {
        items.unshift({
            id: 'in:payload',
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('out')) {
        items.push({
            id: 'out',
            group: 'absolute-out-solid',
            args: { x: JSON_MAP_LAYOUT.width, y: JSON_MAP_LAYOUT.outPortY, dx: 0 },
        });
    }
    return items;
};

export const jsonMapNodeRegistration: NodeRegistration = {
    type: 'jsonMap',
    label: 'JSON 映射 (JsonMap)',
    category: '数据节点',
    color: JSON_MAP_COLOR,
    tagColor: 'purple',
    description:
        '声明式字段映射：从 payload / 上下文路径提取到目标字段。\n\n' +
        '· 左上角 in:payload 传入源对象\n' +
        '· mappings: target ← source 路径\n' +
        '· 输出 $.本节点.out 为映射后的对象',
    sortOrder: 47,
    hasInputs: true,

    shape: {
        shapeName: 'flow-jsonMap',
        kind: 'react',
        component: JsonMapNodeComponent,
        reactPorts: {
            items: [
                { id: 'in:payload', group: 'absolute-in-solid', args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 } },
                { id: 'out', group: 'absolute-out-solid', args: { x: JSON_MAP_LAYOUT.width, y: JSON_MAP_LAYOUT.outPortY, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in:payload', group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: {
            mappings: [{ target: '', source: '' }],
            inputs: {},
            themeColor: 'purple',
        },
        size: { width: JSON_MAP_LAYOUT.width, height: JSON_MAP_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems,
    },

    buildLabel: (data) => {
        const n = Array.isArray(data?.mappings) ? data.mappings.length : 0;
        return n > 0 ? `JsonMap (${n})` : 'JsonMap';
    },

    PropertyEditor: undefined,
};

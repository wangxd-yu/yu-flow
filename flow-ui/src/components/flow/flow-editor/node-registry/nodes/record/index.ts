// ============================================================================
// node-registry/nodes/record
// Record：逐字段 in:var（原逻辑）+ 增量总入口 in:payload
// ============================================================================

import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import {
    RecordNodeComponent,
    RECORD_LAYOUT,
    RECORD_PAYLOAD_PORT_Y,
} from './RecordNodeComponent';

const buildRecordPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        if (seen.has(p.id)) continue;
        // 历史控制流 in 丢弃
        if (p.id === 'in') continue;
        seen.add(p.id);
        const isOut = p.id === 'out' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            group:
                p.group ||
                (isOut ? 'absolute-out-solid' : 'absolute-in-solid'),
        };
        if (p.id === 'out') {
            item.args = { x: RECORD_LAYOUT.width, y: RECORD_LAYOUT.outPortY, dx: 0 };
        } else if (p.id === 'in:payload') {
            item.args = { x: 0, y: RECORD_PAYLOAD_PORT_Y, dx: 0 };
        }
        items.push(item);
    }
    // 保证总入口存在
    if (!seen.has('in:payload')) {
        items.push({
            id: 'in:payload',
            group: 'absolute-in-solid',
            args: { x: 0, y: RECORD_PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    return items;
};

export const recordNodeRegistration: NodeRegistration = {
    type: 'record',
    label: '数据构造 (Record)',
    category: '数据节点',
    color: '#1677ff',
    tagColor: 'blue',
    description:
        '按字段拼装一个对象输出。\n\n' +
        '· 每行：字段名 + 字面量 / 连线路径\n' +
        '· 左上角 in:payload 可整包传入，字段可用相对路径\n' +
        '· 结果从 Result 输出为对象',
    hasInputs: true,

    shape: {
        shapeName: 'flow-record',
        kind: 'react',
        component: RecordNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'in:payload',
                    group: 'absolute-in-solid',
                    args: { x: 0, y: RECORD_PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: RECORD_LAYOUT.width, y: RECORD_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in:payload', group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: {
            schema: {},
            inputs: undefined,
            __fields: [
                {
                    id: 'ph_add',
                    key: '',
                    value: '',
                    source: 'placeholder',
                },
            ],
            themeColor: 'blue',
        },
        size: { width: RECORD_LAYOUT.width, height: RECORD_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildRecordPortItems,
        buildAttrs: () => ({
            body: { stroke: '#1677ff', strokeWidth: 1, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) => {
        const schema = data?.schema;
        if (schema && typeof schema === 'object') {
            const keys = Object.keys(schema).filter(Boolean);
            if (keys.length > 0) {
                return `Record { ${keys.slice(0, 3).join(', ')}${keys.length > 3 ? '…' : ''} }`;
            }
        }
        return 'Record';
    },

    PropertyEditor: undefined,
};

export { RecordNodeComponent, RECORD_LAYOUT, RECORD_PAYLOAD_PORT_Y };
export { RECORD_IN_PORT_Y } from './RecordNodeComponent';

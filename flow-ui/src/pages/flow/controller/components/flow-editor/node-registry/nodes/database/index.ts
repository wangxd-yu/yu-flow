// ============================================================================
// node-registry/nodes/database.ts
// Database (数据库) 节点 —— 自包含注册模块
// ============================================================================

import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import { DatabaseNode, DATABASE_LAYOUT } from './DatabaseNode';
import DatabaseNodeConfig from './DatabaseNodeConfig';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';

const buildDatabasePortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in') continue;
        seen.add(p.id);
        const isOut = p.id === 'out' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            group: p.group || (isOut ? 'absolute-out-solid' : 'absolute-in-solid'),
        };
        if (p.id === 'out') {
            item.args = { x: DATABASE_LAYOUT.width, y: DATABASE_LAYOUT.outPortY, dx: 0 };
        } else if (p.id === PAYLOAD_PORT_ID) {
            item.args = { x: 0, y: PAYLOAD_PORT_Y, dx: 0 };
        }
        items.push(item);
    }
    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.push({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    return items;
};

// ── 注册配置 ──
export const databaseNodeRegistration: NodeRegistration = {
    type: 'database',
    label: '数据库 (Database)',
    category: '调用节点',
    color: '#1677ff',
    tagColor: 'blue',
    hasInputs: true,

    shape: {
        shapeName: 'flow-database',
        kind: 'react',
        component: DatabaseNode,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: PAYLOAD_PORT_ID, group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: { sqlType: 'SELECT', returnType: 'LIST', sql: '', inputs: {} },
        size: { width: DATABASE_LAYOUT.width, height: DATABASE_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildDatabasePortItems,
        buildAttrs: () => ({
            body: { stroke: '#1677ff', strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.sqlType ? `Database: ${data.sqlType}` : 'Database',

    PropertyEditor: DatabaseNodeConfig,
};

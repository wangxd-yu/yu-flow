// ============================================================================
// node-registry/nodes/database.ts
// Database (数据库) 节点 —— 自包含注册模块
// ============================================================================

import type { NodeRegistration } from '../../types';
import { DatabaseNode, DATABASE_LAYOUT } from './DatabaseNode';
import DatabaseNodeConfig from './DatabaseNodeConfig';

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
        reactPorts: {},
    },

    defaults: {
        ports: [], // 端口由组件动态管理
        data: { sqlType: 'SELECT', returnType: 'LIST', sql: '', inputs: {} },
        size: { width: DATABASE_LAYOUT.width, height: DATABASE_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'standard',
        buildAttrs: () => ({
            body: { stroke: '#1677ff', strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.sqlType ? `Database: ${data.sqlType}` : 'Database',

    PropertyEditor: DatabaseNodeConfig,
};

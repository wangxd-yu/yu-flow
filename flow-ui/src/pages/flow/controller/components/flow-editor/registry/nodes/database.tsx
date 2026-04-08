import React from 'react';
import { NodeMetadata } from '@antv/x6';
import type { NodeData } from '../../types';
import type { NodeDefinition } from '../nodeDefinition';
import { createId } from '../../utils/id';
import { DATABASE_LAYOUT } from '../../components/nodes/DatabaseNode';
import type { DatabaseVariable } from '../../components/nodes/DatabaseNode';
import DatabaseNodeConfig from '../../components/configs/DatabaseNodeConfig';

// ---------- helpers ----------
function ensureDatabaseStep(step?: any) {
    return {
        id: step?.id || createId('database'),
        type: 'database' as const,
        name: step?.name ?? 'Database',
        datasourceId: step?.datasourceId ?? undefined,
        sqlType: step?.sqlType ?? 'SELECT',
        returnType: step?.returnType ?? 'LIST',
        sql: step?.sql ?? '',
        inputs: step?.inputs ?? {},
        __variables: step?.__variables ?? [{ id: createId('var'), name: '', extractPath: '' }],
    };
}

export function createDatabaseData(): NodeData {
    const step = ensureDatabaseStep();
    return { kind: 'step', nodeType: 'database', step } as any;
}

function DatabaseProperties(props: {
    data: NodeData;
    onChange: (next: NodeData) => void;
    setValidationError: (msg: string | null) => void;
}) {
    // 如果右侧面板使用 DatabaseNodeConfig，需要 node 实例
    // 这里改为简化的表单，因为 DatabaseNodeConfig 依赖 node
    const { data, onChange, setValidationError } = props;
    const step = (data as any).step as any;

    return (
        <div style={{ fontSize: 12, color: '#8c8c8c', padding: 8 }}>
            <p>数据库节点的配置已内嵌在卡片组件中。</p>
            <p>请直接在画布上编辑 SQL、变量和数据源。</p>
        </div>
    );
}

export const databaseNodeDefinition: NodeDefinition<'database'> = {
    type: 'database',
    label: '数据库 (Database)',
    category: '调用节点',
    buildNodeConfig: (data, position) => {
        const step = (data as any).step;
        return {
            id: step.id,
            shape: 'flow-database',
            x: position?.x ?? 60,
            y: position?.y ?? 40,
            width: DATABASE_LAYOUT.width,
            height: DATABASE_LAYOUT.totalHeight,
            data,
        } as NodeMetadata;
    },
    getDisplayLabel: (data) => {
        const step = (data as any).step;
        return step?.name || 'Database';
    },
    renderProperties: ({ data, onChange, setValidationError }) => (
        <DatabaseProperties data={data} onChange={onChange} setValidationError={setValidationError} />
    ),
};

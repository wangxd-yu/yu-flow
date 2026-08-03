// ============================================================================
// try-catch/index.tsx — TryCatch 节点注册
// ============================================================================

import React from 'react';
import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import { TryCatchNodeComponent, TRY_CATCH_LAYOUT, TRY_CATCH_COLOR } from './TryCatchNodeComponent';
import { PropertyHint, PropertySection } from '../../shared/PropertyPanel';

function TryCatchEditor() {
    return (
        <PropertySection title="Try-Catch">
            <PropertyHint>
                从 <code>try</code> 口扇出受保护子链；子链正常结束走 <code>out</code>。
                子链抛错时写入 <code>$.error</code> 并从 <code>catch</code> 继续，不触发全局 errorHandler。
            </PropertyHint>
        </PropertySection>
    );
}

export const tryCatchNodeRegistration: NodeRegistration = {
    type: 'tryCatch',
    label: 'Try-Catch',
    category: '逻辑节点',
    color: TRY_CATCH_COLOR,
    tagColor: 'purple',
    description:
        '局部错误边界：保护 try 子流。\n\n' +
        '· try：受保护子链入口\n' +
        '· catch：异常补偿链\n' +
        '· out：try 成功后的继续出口',
    sortOrder: 55,
    hasInputs: true,

    shape: {
        shapeName: 'flow-tryCatch',
        kind: 'react',
        component: TryCatchNodeComponent,
        reactPorts: {
            items: [
                { id: 'in', group: 'absolute-in-solid', args: { x: 0, y: TRY_CATCH_LAYOUT.portY.in, dx: 0 } },
                { id: 'try', group: 'absolute-out-solid', args: { x: TRY_CATCH_LAYOUT.width, y: TRY_CATCH_LAYOUT.portY.try, dx: 0 } },
                { id: 'catch', group: 'absolute-out-hollow', args: { x: TRY_CATCH_LAYOUT.width, y: TRY_CATCH_LAYOUT.portY.catch, dx: 0 } },
                { id: 'out', group: 'absolute-out-solid', args: { x: TRY_CATCH_LAYOUT.width, y: TRY_CATCH_LAYOUT.portY.out, dx: 0 } },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'try', group: 'absolute-out-solid' },
            { id: 'catch', group: 'absolute-out-hollow' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: { inputs: {} },
        size: { width: TRY_CATCH_LAYOUT.width, height: TRY_CATCH_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) => {
            const w = TRY_CATCH_LAYOUT.width;
            const map: Record<string, { x: number; y: number; group: string }> = {
                in: { x: 0, y: TRY_CATCH_LAYOUT.portY.in, group: 'absolute-in-solid' },
                try: { x: w, y: TRY_CATCH_LAYOUT.portY.try, group: 'absolute-out-solid' },
                catch: { x: w, y: TRY_CATCH_LAYOUT.portY.catch, group: 'absolute-out-hollow' },
                out: { x: w, y: TRY_CATCH_LAYOUT.portY.out, group: 'absolute-out-solid' },
            };
            return ports
                .filter((p) => map[p.id])
                .map((p) => ({
                    id: p.id,
                    group: map[p.id].group,
                    args: { x: map[p.id].x, y: map[p.id].y, dx: 0 },
                }));
        },
    },

    buildLabel: () => 'TryCatch',

    PropertyEditor: TryCatchEditor,
};

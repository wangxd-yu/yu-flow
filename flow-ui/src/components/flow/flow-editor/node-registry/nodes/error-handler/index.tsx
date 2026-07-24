// ============================================================================
// error-handler/index.tsx — 统一错误处理入口注册
// ============================================================================

import React from 'react';
import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import { ErrorHandlerNodeComponent, ERROR_HANDLER_LAYOUT, ERROR_HANDLER_COLOR } from './ErrorHandlerNodeComponent';
import { PropertyHint, PropertySection } from '../../shared/PropertyPanel';

function ErrorHandlerEditor() {
    return (
        <PropertySection title="错误处理">
            <PropertyHint>
                本节点<strong>不接入主流程 in</strong>。运行时若抛出异常，引擎会跳转到此节点，
                并将错误写入 <code>error</code>（message / code / stepId）与本节点 <code>out</code>。
                请从 out 连接补偿逻辑或 Response。画布仅允许一个。
            </PropertyHint>
        </PropertySection>
    );
}

export const errorHandlerNodeRegistration: NodeRegistration = {
    type: 'errorHandler',
    label: '错误处理 (ErrorHandler)',
    category: '逻辑节点',
    color: ERROR_HANDLER_COLOR,
    tagColor: 'red',
    description:
        '统一错误处理入口。\n\n' +
        '· 不连主流程 in；异常时引擎自动跳转\n' +
        '· 上下文 error：message / code / stepId\n' +
        '· 从其 out 接补偿或 Response；单例',
    sortOrder: 50,
    singleton: true,
    hasInputs: false,

    shape: {
        shapeName: 'flow-errorHandler',
        kind: 'react',
        component: ErrorHandlerNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: ERROR_HANDLER_LAYOUT.width, y: ERROR_HANDLER_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [{ id: 'out', group: 'absolute-out-solid' }],
        data: {},
        size: { width: ERROR_HANDLER_LAYOUT.width, height: ERROR_HANDLER_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: (ports: DslPort[]) =>
            ports
                .filter((p) => p.id === 'out')
                .map((p) => ({
                    id: p.id,
                    group: 'absolute-out-solid',
                    args: { x: ERROR_HANDLER_LAYOUT.width, y: ERROR_HANDLER_LAYOUT.outPortY, dx: 0 },
                })),
    },

    buildLabel: () => 'ErrorHandler',

    PropertyEditor: ErrorHandlerEditor,
};

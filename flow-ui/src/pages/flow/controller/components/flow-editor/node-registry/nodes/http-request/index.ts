// ============================================================================
// node-registry/nodes/http-request
// HttpRequest (HTTP 请求) —— 中间调用节点（非流程起点）
// ============================================================================

import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import {
    HttpRequestNodeComponent,
    HTTP_REQUEST_LAYOUT,
    HTTP_REQUEST_IN_PORT_Y,
} from './HttpRequestNodeComponent';

/** 导入时不挂 X6 端口文字（文案由 React 节点内绘制，避免 IN / success / fail 重影） */
const buildHttpRequestPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        if (seen.has(p.id)) continue;
        seen.add(p.id);
        const isOut = p.id === 'success' || p.id === 'fail' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            group: p.group
                || (p.id === 'fail' ? 'absolute-out-hollow' : isOut ? 'absolute-out-solid' : 'absolute-in-solid'),
        };
        if (p.id === 'in') {
            item.args = { x: 0, y: HTTP_REQUEST_IN_PORT_Y, dx: 0 };
        }
        items.push(item);
    }
    return items;
};

export const httpRequestNodeRegistration: NodeRegistration = {
    type: 'httpRequest',
    label: 'HTTP 请求',
    category: '调用节点',
    color: '#fa8c16',
    tagColor: 'orange',
    hasInputs: false,

    shape: {
        shapeName: 'flow-httpRequest',
        kind: 'react',
        component: HttpRequestNodeComponent,
        reactPorts: {
            items: [
                {
                    id: 'in',
                    group: 'absolute-in-solid',
                    args: { x: 0, y: HTTP_REQUEST_IN_PORT_Y, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: 'in', group: 'absolute-in-solid' },
            { id: 'success', group: 'absolute-out-solid' },
            { id: 'fail', group: 'absolute-out-hollow' },
        ],
        data: {
            url: '',
            method: 'GET',
            timeout: 10000,
            bodyType: 'json',
            successCondition: 'status == 200',
        },
        size: { width: HTTP_REQUEST_LAYOUT.width, height: 280 },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildHttpRequestPortItems,
        buildAttrs: () => ({
            body: { stroke: '#fa8c16', strokeWidth: 1, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.url
            ? `${data.method || 'GET'} ${String(data.url).substring(0, 20)}`
            : 'HTTP Request',

    PropertyEditor: undefined,
};

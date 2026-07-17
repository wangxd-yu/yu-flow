// ============================================================================
// node-registry/nodes/http-request
// HttpRequest (HTTP 请求) —— 中间调用节点（非流程起点）
// ============================================================================

import type { DslPort } from '../../../types';
import type { NodeRegistration } from '../../types';
import {
    HttpRequestNodeComponent,
    HTTP_REQUEST_LAYOUT,
} from './HttpRequestNodeComponent';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';

/** 导入时不挂 X6 端口文字（文案由 React 节点内绘制，避免 IN / success / fail 重影） */
const buildHttpRequestPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        // 历史控制流 in 丢弃，统一用 in:payload
        if (seen.has(p.id) || p.id === 'in') continue;
        seen.add(p.id);
        const isOut = p.id === 'success' || p.id === 'fail' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            group: p.group
                || (p.id === 'fail' ? 'absolute-out-hollow' : isOut ? 'absolute-out-solid' : 'absolute-in-solid'),
        };
        if (p.id === PAYLOAD_PORT_ID) {
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
            { id: 'success', group: 'absolute-out-solid' },
            { id: 'fail', group: 'absolute-out-hollow' },
        ],
        data: {
            url: '',
            method: 'GET',
            timeout: 10000,
            bodyType: 'json',
            successCondition: 'status == 200',
            logEnabled: true,
            apiType: '',
            ignoreSsl: true,
            inputs: {},
        },
        size: { width: HTTP_REQUEST_LAYOUT.width, height: 320 },
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

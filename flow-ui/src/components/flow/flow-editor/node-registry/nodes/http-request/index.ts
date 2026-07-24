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
import { HttpRequestPropertyEditor } from './HttpRequestPropertyEditor';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';

/** HttpRequest 允许的 X6 端口（inputs 变量走 extractPath，不挂 in:var 可视桩） */
function isHttpRequestVisualPort(id: string): boolean {
    if (!id || id === 'in' || id.startsWith('in:var:')) return false;
    if (id === PAYLOAD_PORT_ID || id === 'success' || id === 'fail') return true;
    if (id === 'in:body:json') return true;
    return id.startsWith('in:header:')
        || id.startsWith('in:param:')
        || id.startsWith('in:form:');
}

/** 导入时不挂 X6 端口文字（文案由 React 节点内绘制，避免 IN / success / fail 重影） */
const buildHttpRequestPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        // 历史控制流 in、adapter 生成的 in:var（无 args → 左上角幽灵点）一律丢弃
        if (!isHttpRequestVisualPort(p.id) || seen.has(p.id)) continue;
        seen.add(p.id);
        const isOut = p.id === 'success' || p.id === 'fail' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            // 强制 absolute-*，避免脏 DSL 的 manual/left 落到 (0,0)
            group: p.id === 'fail' ? 'absolute-out-hollow' : isOut ? 'absolute-out-solid' : 'absolute-in-solid',
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
    description:
        '向外部 HTTP 地址发起请求。\n\n' +
        '· 配置 Method、URL、Headers、Params、Body\n' +
        '· 鉴权（Bearer / Basic / API Key）在右侧属性面板\n' +
        '· 成功走 success，失败走 fail\n' +
        '· 超时/重试等策略在右侧属性面板配置\n' +
        '· 与「API 调用」不同：此处可任意指定外部 URL',
    /** 右侧展示 inputs 映射（baseUrl / token 等）+ 请求策略 / 鉴权 */
    hasInputs: true,

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
            timeout: 30000,
            retryCount: 0,
            retryIntervalMs: 1000,
            bodyType: 'json',
            successCondition: 'status == 200',
            logEnabled: false,
            apiType: '',
            ignoreSsl: false,
            authType: 'none',
            authToken: '',
            authUsername: '',
            authPassword: '',
            authApiKeyIn: 'header',
            authApiKeyName: '',
            authApiKeyValue: '',
            inputs: {},
        },
        size: { width: HTTP_REQUEST_LAYOUT.width, height: 300 },
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

    PropertyEditor: HttpRequestPropertyEditor,
};

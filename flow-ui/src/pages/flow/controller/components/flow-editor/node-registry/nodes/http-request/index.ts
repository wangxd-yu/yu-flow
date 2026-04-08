// ============================================================================
// node-registry/nodes/http-request.ts
// HttpRequest (HTTP 请求) 节点 —— 自包含注册模块
// ============================================================================

import type { NodeRegistration } from '../../types';
import { HttpRequestNodeComponent, HTTP_REQUEST_LAYOUT } from './HttpRequestNodeComponent';

// ── 注册配置 ──
// 注意：HttpRequestNodeComponent 内部通过 useEffect 动态管理所有端口，
// 不在注册时声明固定端口；size 使用 width 宽度，初始高度给一个合理默认值。
export const httpRequestNodeRegistration: NodeRegistration = {
    type: 'httpRequest',
    label: 'HTTP 请求',
    category: '调用节点',
    color: '#fa8c16',
    tagColor: 'orange',
    hasInputs: false, // HTTP Request 节点内部直接管理输入，无需通用 inputs 映射

    shape: {
        shapeName: 'flow-httpRequest',
        kind: 'react',
        component: HttpRequestNodeComponent,
        // 初始端口留空；组件内 useEffect 动态增删端口
        reactPorts: {
            items: [],
        },
    },

    defaults: {
        ports: [],
        data: {
            url: '',
            method: 'GET',
            timeout: 10000,
            bodyType: 'json',
            successCondition: 'status == 200',
        },
        size: { width: HTTP_REQUEST_LAYOUT.width, height: 300 }, // 初始高度，组件会自动 resize
    },

    importConfig: {
        portMode: 'standard',
        buildAttrs: () => ({
            body: { stroke: '#fa8c16', strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.url
            ? `${data.method || 'GET'} ${String(data.url).substring(0, 20)}`
            : 'HTTP Request',

    // HttpRequestNodeComponent 自身包含完整配置 UI，
    // 属性面板无需额外 PropertyEditor
    PropertyEditor: undefined,
};

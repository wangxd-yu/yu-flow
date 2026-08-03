// ============================================================================
// mq-send/index.tsx — 发送 MQ 消息节点注册（in:payload + 变量行）
// ============================================================================

import React from 'react';
import { AutoComplete, Input, Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { MqSendNodeComponent, MQ_SEND_LAYOUT, MQ_SEND_COLOR } from './MqSendNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import { NODE_FOOTER_HEIGHT, NODE_FOOTER_PORT_OFFSET_Y } from '../../shared/useNodeSelection';
import { queryMqConnectionOptions, queryMqTopics } from '@/services/flow/mqConnection';

function MqSendEditor({ data, onChange }: PropertyEditorProps) {
    const [connOptions, setConnOptions] = React.useState<{ label: string; value: string }[]>([]);
    const [topicOptions, setTopicOptions] = React.useState<{ value: string }[]>([]);
    const searchTimer = React.useRef<any>(null);
    React.useEffect(() => {
        queryMqConnectionOptions()
            .then((list: any[]) => {
                setConnOptions(
                    (list || []).map((c: any) => ({
                        label: `${c.name} (${c.mqType})`,
                        value: c.code,
                    })),
                );
            })
            .catch(() => setConnOptions([]));
    }, []);

    // topic 候选仅作提示：拉取失败或不支持枚举时静默降级为手工输入
    const loadTopics = React.useCallback((keyword?: string) => {
        const code = data.connectionCode;
        if (!code) {
            setTopicOptions([]);
            return;
        }
        queryMqTopics(code, keyword, 50)
            .then((res: any) => {
                const list = res?.data ?? res;
                setTopicOptions((Array.isArray(list) ? list : []).map((t: string) => ({ value: t })));
            })
            .catch(() => setTopicOptions([]));
    }, [data.connectionCode]);

    React.useEffect(() => {
        setTopicOptions([]);
        loadTopics();
    }, [loadTopics]);

    React.useEffect(() => () => {
        if (searchTimer.current) clearTimeout(searchTimer.current);
    }, []);

    const handleTopicSearch = (keyword: string) => {
        if (searchTimer.current) clearTimeout(searchTimer.current);
        searchTimer.current = setTimeout(() => loadTopics(keyword), 300);
    };

    return (
        <PropertySection title="MQ 发送配置">
            <PropertyHint>
                左上角 <code>in:payload</code> 为数据总入口；卡片变量行可逐项映射。topic /
                messageKey / message 支持 <code>{'${var}'}</code>（var 来自 inputs）。连接在「MQ
                连接管理」中维护。
            </PropertyHint>
            <PropertyField label="MQ 连接 connectionCode">
                <Select
                    size="small"
                    style={{ width: '100%' }}
                    value={data.connectionCode || undefined}
                    placeholder="选择 MQ 连接..."
                    options={connOptions}
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    getPopupContainer={() => document.body}
                    onChange={(v) => onChange({ connectionCode: v || '' })}
                />
            </PropertyField>
            <PropertyField label="Topic / RoutingKey">
                <AutoComplete
                    size="small"
                    style={{ width: '100%' }}
                    value={data.topic || ''}
                    options={topicOptions}
                    placeholder="order.created 或 ${topicVar}"
                    filterOption={false}
                    notFoundContent={null}
                    getPopupContainer={() => document.body}
                    onSearch={handleTopicSearch}
                    onChange={(v) => onChange({ topic: v || '' })}
                />
            </PropertyField>
            <PropertyField label="消息 Key" extra="可选，Kafka 分区键 / Rabbit messageId">
                <Input
                    size="small"
                    value={data.messageKey || ''}
                    placeholder="${orderId}"
                    onChange={(e) => onChange({ messageKey: e.target.value })}
                />
            </PropertyField>
            <PropertyField label="消息体 message">
                <Input.TextArea
                    size="small"
                    rows={5}
                    value={data.message || ''}
                    placeholder={'{"orderId":"${orderId}","status":"PAID"}'}
                    onChange={(e) => onChange({ message: e.target.value })}
                />
            </PropertyField>
        </PropertySection>
    );
}

const buildMqSendPortItems = (ports: DslPort[]) => {
    const w = MQ_SEND_LAYOUT.width;
    const outY = MQ_SEND_LAYOUT.height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    const seen = new Set<string>();
    const items: any[] = [];

    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in' || p.id.startsWith('in:var:')) continue;
        seen.add(p.id);
        if (p.id === PAYLOAD_PORT_ID) {
            items.push({
                id: PAYLOAD_PORT_ID,
                group: 'absolute-in-solid',
                args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
            });
        } else if (p.id === 'success' || p.id === 'out') {
            items.push({
                id: 'success',
                group: 'absolute-out-solid',
                args: { x: w, y: MQ_SEND_LAYOUT.successPortY(MQ_SEND_LAYOUT.height), dx: 0 },
            });
        } else if (p.id === 'fail') {
            items.push({
                id: 'fail',
                group: 'absolute-out-hollow',
                args: { x: w, y: MQ_SEND_LAYOUT.failPortY(MQ_SEND_LAYOUT.height), dx: 0 },
            });
        }
    }
    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.unshift({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('success') && !seen.has('out')) {
        items.push({
            id: 'success',
            group: 'absolute-out-solid',
            args: { x: w, y: MQ_SEND_LAYOUT.successPortY(MQ_SEND_LAYOUT.height), dx: 0 },
        });
        items.push({
            id: 'fail',
            group: 'absolute-out-hollow',
            args: { x: w, y: MQ_SEND_LAYOUT.failPortY(MQ_SEND_LAYOUT.height), dx: 0 },
        });
    }
    return items;
};

export const mqSendNodeRegistration: NodeRegistration = {
    type: 'mqSend',
    label: '发送消息 (MQ Send)',
    category: '调用节点',
    color: MQ_SEND_COLOR,
    tagColor: 'cyan',
    description:
        '向 RabbitMQ / Kafka 发送一条消息。\n\n' +
        '· 左上角 in:payload 总入口；卡片内可新增变量行\n' +
        '· topic / messageKey / message 支持 ${变量名}\n' +
        '· 成功走 success，失败走 fail',
    sortOrder: 46,
    hasInputs: true,

    shape: {
        shapeName: 'flow-mq-send',
        kind: 'react',
        component: MqSendNodeComponent,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'success',
                    group: 'absolute-out-solid',
                    args: { x: MQ_SEND_LAYOUT.width, y: MQ_SEND_LAYOUT.successPortY(MQ_SEND_LAYOUT.height), dx: 0 },
                },
                {
                    id: 'fail',
                    group: 'absolute-out-hollow',
                    args: { x: MQ_SEND_LAYOUT.width, y: MQ_SEND_LAYOUT.failPortY(MQ_SEND_LAYOUT.height), dx: 0 },
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
            connectionCode: '',
            topic: '',
            messageKey: '',
            message: '',
            inputs: {},
        },
        size: { width: MQ_SEND_LAYOUT.width, height: MQ_SEND_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildMqSendPortItems,
    },

    buildLabel: (data) =>
        data.topic ? `MQ: ${String(data.topic).slice(0, 24)}` : 'MQ Send',

    PropertyEditor: MqSendEditor,
};

export default mqSendNodeRegistration;

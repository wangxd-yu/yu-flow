// ============================================================================
// node-registry/nodes/api
// API Call：调用另一条内部 Flow API（编排组合）
// ============================================================================

import React from 'react';
import { Input, Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ApiNodeComponent, API_LAYOUT } from './ApiNodeComponent';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';
import { queryAutoApiConfigList } from '@/pages/flow/controller/services/flowController';

const buildApiPortItems = (ports: DslPort[]) => {
    const seen = new Set<string>();
    const items: any[] = [];
    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in' || p.id.startsWith('in:var:')) continue;
        seen.add(p.id);
        const isOut = p.id === 'out' || p.id.startsWith('out');
        const item: any = {
            id: p.id,
            group: p.group || (isOut ? 'absolute-out-solid' : 'absolute-in-solid'),
        };
        if (p.id === 'out') {
            item.args = { x: API_LAYOUT.width, y: API_LAYOUT.outPortY, dx: 0 };
        } else if (p.id === PAYLOAD_PORT_ID) {
            item.args = { x: 0, y: PAYLOAD_PORT_Y, dx: 0 };
        } else {
            continue;
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
    if (!seen.has('out')) {
        items.push({
            id: 'out',
            group: 'absolute-out-solid',
            args: { x: API_LAYOUT.width, y: API_LAYOUT.outPortY, dx: 0 },
        });
    }
    return items;
};

function ApiPropertyEditor({ data, onChange }: PropertyEditorProps) {
    const [options, setOptions] = React.useState<{ label: string; value: string }[]>([]);
    const [loading, setLoading] = React.useState(false);

    const load = React.useCallback(async (keyword?: string) => {
        setLoading(true);
        try {
            const res: any = await queryAutoApiConfigList({
                page: 0,
                size: 50,
                name: keyword || undefined,
            });
            const items = res?.items || res?.data?.items || [];
            setOptions(
                items.map((item: any) => ({
                    label: item.name
                        ? `${item.name}${item.url ? `  (${item.method || ''} ${item.url})` : ''}`
                        : item.id,
                    value: item.id,
                })),
            );
        } catch {
            setOptions([]);
        } finally {
            setLoading(false);
        }
    }, []);

    React.useEffect(() => {
        load();
    }, [load]);

    const selectOptions = React.useMemo(() => {
        const sid = data.serviceId;
        if (!sid) return options;
        if (options.some((o) => o.value === sid)) return options;
        return [{ value: sid, label: data.__serviceName || sid }, ...options];
    }, [options, data.serviceId, data.__serviceName]);

    return (
        <PropertySection
            title="API 调用配置"
            tip="内部编排：直接执行另一条 Flow API，Method/URL 用目标 API 自己的配置，无需在此选择"
        >
            <PropertyField label="目标 API">
                <Select
                    size="small"
                    showSearch
                    allowClear
                    loading={loading}
                    value={data.serviceId || undefined}
                    placeholder="选择内部 Flow API..."
                    options={selectOptions}
                    filterOption={false}
                    onSearch={(kw) => load(kw)}
                    onChange={(val, option: any) => {
                        const opt = Array.isArray(option) ? option[0] : option;
                        onChange({
                            serviceId: val || '',
                            __serviceName: typeof opt?.label === 'string' ? opt.label : '',
                        });
                    }}
                    style={{ width: '100%' }}
                    getPopupContainer={() => document.body}
                />
            </PropertyField>
            <PropertyField
                label="output"
                tip="可选：额外将返回值写入该上下文变量名；主结果始终在 {nodeId}.out"
            >
                <Input
                    size="small"
                    value={data.output || ''}
                    placeholder="例如 detail（可选）"
                    onChange={(e) => onChange({ output: e.target.value })}
                />
            </PropertyField>
            <PropertyHint>
                选择目标 API 后会从契约自动带出 query/path/body 入参（可点同步刷新）。
                右栏填本流程来源；执行时按来源写入被调侧 @QP/@PP/@BP。下游取 $.本节点.out
            </PropertyHint>
        </PropertySection>
    );
}

export const apiNodeRegistration: NodeRegistration = {
    type: 'api',
    label: 'API 调用 (API Call)',
    category: '调用节点',
    color: '#2f54eb',
    tagColor: 'blue',
    description:
        '调用另一条已配置的内部 Flow API（编排组合）。\n\n' +
        '· 选择目标 API 后，会从契约自动带出 query / path / body 入参\n' +
        '· 右侧填写本流程数据来源（如 $.request.params.id）\n' +
        '· Method/URL 沿用目标 API，无需在此选择\n' +
        '· 结果从右侧 Result 输出，下游用 $.本节点.out 读取',
    hasInputs: true,

    shape: {
        shapeName: 'flow-api',
        kind: 'react',
        component: ApiNodeComponent,
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
        data: {
            serviceId: '',
            __serviceName: '',
            output: '',
            inputs: {},
            themeColor: 'blue',
        },
        size: { width: API_LAYOUT.width, height: API_LAYOUT.totalHeight },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildApiPortItems,
        buildAttrs: () => ({
            body: { stroke: '#2f54eb', strokeWidth: 2, fill: '#ffffff' },
        }),
    },

    buildLabel: (data) =>
        data.__serviceName
            ? `API: ${String(data.__serviceName).split('  (')[0]}`
            : data.serviceId
              ? `API: ${data.serviceId}`
              : 'API Call',

    PropertyEditor: ApiPropertyEditor,
};

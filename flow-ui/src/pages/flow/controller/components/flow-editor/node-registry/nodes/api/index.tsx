// ============================================================================
// node-registry/nodes/api
// API Call：调用另一条内部 Flow API 或内部服务（编排组合）
// ============================================================================

import React from 'react';
import { Input, Select } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { ApiNodeComponent, API_LAYOUT } from './ApiNodeComponent';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';
import { queryAutoApiConfigList } from '@/pages/flow/controller/services/flowController';
import { queryServiceFlowPage } from '@/pages/flow/service/services/serviceFlowService';

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
    const targetType: 'api' | 'service' = data.targetType === 'service' ? 'service' : 'api';
    const [options, setOptions] = React.useState<{ label: string; value: string }[]>([]);
    const [loading, setLoading] = React.useState(false);

    const load = React.useCallback(async (keyword?: string) => {
        setLoading(true);
        try {
            if (targetType === 'service') {
                const res: any = await queryServiceFlowPage({
                    page: 0,
                    size: 50,
                    name: keyword || undefined,
                    enabled: true,
                    publishStatus: 1,
                });
                const items = res?.items || res?.data?.items || [];
                setOptions(
                    items.map((item: any) => ({
                        label: item.name || item.id,
                        value: item.id,
                    })),
                );
            } else {
                const res: any = await queryAutoApiConfigList({
                    page: 0,
                    size: 50,
                    name: keyword || undefined,
                    publishStatus: 1,
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
            }
        } catch {
            setOptions([]);
        } finally {
            setLoading(false);
        }
    }, [targetType]);

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
            title="编排调用配置"
            tip="可调用内部 Flow API，或服务编排中的内部服务"
        >
            <PropertyField label="目标类型">
                <Select
                    size="small"
                    value={targetType}
                    options={[
                        { value: 'api', label: '接口 API' },
                        { value: 'service', label: '内部服务' },
                    ]}
                    onChange={(val: 'api' | 'service') => {
                        onChange({
                            targetType: val,
                            serviceId: '',
                            __serviceName: '',
                            __serviceMethod: '',
                            __serviceUrl: '',
                        });
                    }}
                    style={{ width: '100%' }}
                    getPopupContainer={() => document.body}
                />
            </PropertyField>
            <PropertyField label={targetType === 'service' ? '目标服务' : '目标 API'}>
                <Select
                    size="small"
                    showSearch
                    allowClear
                    loading={loading}
                    value={data.serviceId || undefined}
                    placeholder={targetType === 'service' ? '选择内部服务...' : '选择内部 Flow API...'}
                    options={selectOptions}
                    filterOption={false}
                    onSearch={(kw) => load(kw)}
                    onChange={(val, option: any) => {
                        const opt = Array.isArray(option) ? option[0] : option;
                        onChange({
                            targetType,
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
                {targetType === 'service'
                    ? '选择服务后按契约自动带出入参，执行时写入 $.service.input；下游取 $.本节点.out'
                    : '选择目标 API 后会从契约自动带出 query/path/body 入参。下游取 $.本节点.out'}
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
        '调用另一条已配置的内部 Flow API，或服务编排中的内部服务。\n\n' +
        '· 目标类型可选「接口 API」或「内部服务」\n' +
        '· API：从契约自动带出入参；服务：手动配置入参 → $.service.input\n' +
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
            targetType: 'api',
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

    buildLabel: (data) => {
        const name = data.__serviceName
            ? String(data.__serviceName).split('  (')[0]
            : data.serviceId
              ? String(data.serviceId)
              : '';
        if (data.targetType === 'service') {
            return name ? `Svc: ${name}` : 'Service Call';
        }
        return name ? `API: ${name}` : 'API Call';
    },

    PropertyEditor: ApiPropertyEditor,
};

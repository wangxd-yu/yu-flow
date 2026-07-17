// ============================================================================
// RecordNodeComponent.tsx
// Postman Record 风格（对齐 Evaluate 占位连线）：
//   - 无控制流 in：由字段口连线驱动执行顺序（同 Evaluate）
//   - 传入 wire：in:var 端口 + 左侧凸起
//   - 固定 literal：无端口；底部「+」选类型新增
//   - 末行 placeholder：可连线 → 升级为 wire，并下推新占位行
// ============================================================================

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Dropdown, Input, InputNumber, Select, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { Node } from '@antv/x6';
import {
    NodeHeader,
    NodeWrapper,
    useNodeSelection,
    getNodeTheme,
    ResizeHandle,
    NODE_HEADER_WITH_ID_HEIGHT,
} from '../../shared/useNodeSelection';
import { relativizeExtractPath } from '../../shared/extractPathUtils';
import { commitFlowNodeIdChange } from '../../shared/nodeIdUtils';
import { createId } from '../../../utils/id';

const { Text } = Typography;

export type RecordValueType = 'string' | 'number' | 'boolean' | 'null';
export type RecordFieldSource = 'wire' | 'literal' | 'placeholder';

export interface RecordField {
    id: string;
    key: string;
    value: string;
    source: RecordFieldSource;
    valueType?: RecordValueType;
}

const HEADER_HEIGHT = NODE_HEADER_WITH_ID_HEIGHT;
const ROW_HEIGHT = 40;
const PADDING_Y = 8;
const FOOTER_HEIGHT = 44;
const FT_RESULT_Y = 12;
const MIN_WIDTH = 320;

/** 按字段行数计算节点总高度（无手动缩放，随属性自动撑开） */
export const calcRecordHeight = (fieldCount: number) =>
    HEADER_HEIGHT + PADDING_Y + Math.max(fieldCount, 1) * ROW_HEIGHT + PADDING_Y + FOOTER_HEIGHT;

const MIN_HEIGHT = calcRecordHeight(1);

/** 总入口 in:payload（Header 左侧）—— 整包传入，字段可相对解析 */
export const RECORD_PAYLOAD_PORT_Y = HEADER_HEIGHT / 2;
/** @deprecated 兼容旧导出；现为 payload 口 Y */
export const RECORD_IN_PORT_Y = RECORD_PAYLOAD_PORT_Y;

export const RECORD_LAYOUT = {
    width: MIN_WIDTH,
    headerHeight: HEADER_HEIGHT,
    footerHeight: FOOTER_HEIGHT,
    rowHeight: ROW_HEIGHT,
    inPortY: RECORD_PAYLOAD_PORT_Y,
    payloadPortY: RECORD_PAYLOAD_PORT_Y,
    get outPortY() {
        return MIN_HEIGHT - FOOTER_HEIGHT + FT_RESULT_Y;
    },
    get totalHeight() {
        return MIN_HEIGHT;
    },
};

/** 卡片 / 弹框共用：固定宽度 + 统一字形 */
const TYPE_BADGE_WIDTH = 32;

const TYPE_BADGE_LABEL: Record<RecordValueType | 'path', string> = {
    string: 'Aa',
    number: '123',
    boolean: '0/1',
    null: '∅',
    path: '◎',
};

const TYPE_MENU_ITEMS: { key: RecordValueType | 'path'; label: string }[] = [
    { key: 'path', label: 'Path' },
    { key: 'string', label: 'String' },
    { key: 'number', label: 'Number' },
    { key: 'boolean', label: 'Bool' },
    { key: 'null', label: 'Null' },
];

/** 绝对上下文路径（走 in:var / prepareInputs）；其余视为相对 payload */
const isAbsoluteExtractPath = (p: string) => {
    const t = (p || '').trim();
    if (!t) return false;
    if (t.startsWith('${') || t.startsWith("$['")) return true;
    if (t === '$') return true; // 刚连线占位，交给 FlowParser 展开
    if (t.startsWith('$.')) {
        const parts = t.slice(2).split('.').filter(Boolean);
        return parts.length >= 2; // $.schedule.triggerTime
    }
    return false;
};

const ICONS = {
    record: (
        <svg viewBox="0 0 1024 1024" width="14" height="14" fill="currentColor">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-207.3 0-376-168.7-376-376S304.7 132 512 132s376 168.7 376 376-168.7 376-376 376z" />
            <path d="M512 320c-106 0-192 86-192 192s86 192 192 192 192-86 192-192-86-192-192-192zm0 288c-53 0-96-43-96-96s43-96 96-96 96 43 96 96-43 96-96 96z" />
        </svg>
    ),
    drag: (
        <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor">
            <path d="M384 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m384-512a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z m0 256a64 64 0 1 1-128 0 64 64 0 0 1 128 0z" />
        </svg>
    ),
    selectPath: (
        <svg viewBox="0 0 1024 1024" width="12" height="12" fill="currentColor">
            <path d="M512 128a384 384 0 1 0 0.1 768.1A384 384 0 0 0 512 128zm0 704a320 320 0 1 1 0-640 320 320 0 0 1 0 640zm0-480a160 160 0 1 0 0.1 320.1A160 160 0 0 0 512 352z" />
        </svg>
    ),
};

const typeBadge = (kind: RecordValueType | 'path', opts?: { color?: string }) => {
    const color = opts?.color ?? '#bfbfbf';
    if (kind === 'path') {
        return (
            <span
                style={{
                    color,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: TYPE_BADGE_WIDTH,
                    minWidth: TYPE_BADGE_WIDTH,
                    flexShrink: 0,
                }}
            >
                {ICONS.selectPath}
            </span>
        );
    }
    return (
        <span
            style={{
                color,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: TYPE_BADGE_WIDTH,
                minWidth: TYPE_BADGE_WIDTH,
                flexShrink: 0,
                fontSize: 11,
                fontWeight: 600,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                letterSpacing: kind === 'number' || kind === 'boolean' ? -0.5 : 0,
                lineHeight: 1,
            }}
        >
            {TYPE_BADGE_LABEL[kind]}
        </span>
    );
};

const fieldPortY = (idx: number) =>
    HEADER_HEIGHT + PADDING_Y + idx * ROW_HEIGHT + ROW_HEIGHT / 2;

const defaultLiteralValue = (t: RecordValueType): string => {
    if (t === 'boolean') return 'true';
    if (t === 'number') return '0';
    if (t === 'null') return '';
    return '';
};

const schemaFromFields = (fields: RecordField[]): Record<string, any> => {
    const schema: Record<string, any> = {};
    fields.forEach((f) => {
        if (f.source === 'placeholder' || !f.key?.trim()) return;
        const key = f.key.trim();
        if (f.source === 'wire') {
            // 空路径允许；`$` 表示刚连线的绝对占位（FlowParser 会展开）
            schema[key] = (f.value ?? '').trim();
            return;
        }
        const vt = f.valueType || 'string';
        if (vt === 'null') schema[key] = null;
        else if (vt === 'number') {
            const n = Number(f.value);
            schema[key] = Number.isFinite(n) ? n : 0;
        } else if (vt === 'boolean') schema[key] = f.value === 'true' || f.value === '1';
        else schema[key] = f.value ?? '';
    });
    return schema;
};

/**
 * 仅把「绝对路径」wire 写入 inputs（保持原有逐字段连线逻辑）。
 * 相对路径（如 apiid / $.apiid）只放 schema，由执行器相对 payload 解析。
 */
const inputsFromFields = (fields: RecordField[]): Record<string, any> => {
    const r: Record<string, any> = {};
    for (const f of fields) {
        if (f.source === 'wire' && f.key?.trim()) {
            const path = f.value?.trim() ? f.value.trim() : '$';
            if (isAbsoluteExtractPath(path)) {
                r[f.key.trim()] = { id: f.id, extractPath: path };
            }
        }
    }
    return r;
};

const mergeInputs = (
    fields: RecordField[],
    prevInputs?: Record<string, any>,
): Record<string, any> | undefined => {
    const next = inputsFromFields(fields);
    if (prevInputs?.payload != null) {
        next.payload = prevInputs.payload;
    }
    return Object.keys(next).length > 0 ? next : undefined;
};

const inferSource = (val: any, hasPort: boolean): RecordFieldSource => {
    if (hasPort) return 'wire';
    if (typeof val === 'string') {
        const t = val.trim();
        if (t === '$' || t.startsWith('$.') || t.startsWith('$[') || t.startsWith('${')) {
            return 'wire';
        }
    }
    if (val && typeof val === 'object' && (val as any).extractPath) return 'wire';
    return 'literal';
};

const inferValueType = (val: any): RecordValueType => {
    if (val === null) return 'null';
    if (typeof val === 'number') return 'number';
    if (typeof val === 'boolean') return 'boolean';
    return 'string';
};

const normalizeFields = (
    raw: any[],
    schema?: Record<string, any>,
    existingPorts?: string[],
): RecordField[] => {
    const portIds = new Set(
        (existingPorts || [])
            .filter((p) => p.startsWith('in:var:') || p.startsWith('in:field:'))
            .map((p) => p.replace(/^in:(var|field):/, '')),
    );

    let list: RecordField[] = [];
    if (Array.isArray(raw) && raw.length > 0) {
        list = raw.map((f) => {
            const source: RecordFieldSource =
                f.source === 'placeholder' || f.source === 'wire' || f.source === 'literal'
                    ? f.source
                    : inferSource(f.value, portIds.has(f.id));
            return {
                id: f.id || createId('f'),
                key: f.key || '',
                value: f.value == null ? '' : String(f.value),
                source,
                valueType: f.valueType || inferValueType(
                    schema && f.key ? schema[f.key] : f.value,
                ),
            } as RecordField;
        });
    } else if (schema && typeof schema === 'object') {
        list = Object.entries(schema).map(([key, val]) => {
            const id =
                (val && typeof val === 'object' && (val as any).id) || createId('f');
            const extract =
                typeof val === 'string'
                    ? val
                    : val && typeof val === 'object'
                        ? String((val as any).extractPath ?? '')
                        : val == null
                            ? ''
                            : String(val);
            return {
                id: String(id),
                key,
                value: extract,
                source: inferSource(val, portIds.has(String(id))),
                valueType: inferValueType(val),
            };
        });
    }

    const existingPh = (Array.isArray(raw) ? raw : []).find(
        (f) => f?.source === 'placeholder' && f?.id,
    );
    list = list.filter((f) => f.source !== 'placeholder');
    // 对齐 Evaluate：末行永远是可连线占位
    list.push({
        id: existingPh?.id || createId('f'),
        key: '',
        value: '',
        source: 'placeholder',
    });
    return list;
};

export const RecordNodeComponent = ({ node }: { node: Node }) => {
    const [data, setData] = useState<any>(node.getData());
    const themeObj = getNodeTheme(data?.themeColor || 'blue');
    const { outlineCss, borderColor, selected } = useNodeSelection(node, {
        defaultColor: themeObj.primary,
        selectedColor: themeObj.primary,
    });

    const [hoverRowIndex, setHoverRowIndex] = useState<number | null>(null);
    const [dragState, setDragState] = useState<{
        index: number;
        startY: number;
        currentY: number;
    } | null>(null);

    const [fields, setFields] = useState<RecordField[]>(() => {
        const d = node.getData() as any;
        return normalizeFields(
            d?.__fields,
            d?.schema,
            node.getPorts().map((p) => p.id!),
        );
    });

    const fieldsRef = useRef(fields);
    fieldsRef.current = fields;

    const syncFields = useCallback(
        (next: RecordField[]) => {
            let normalized = next.filter((f) => f.source !== 'placeholder');
            const ph = next.find((f) => f.source === 'placeholder');
            normalized.push(
                ph || { id: createId('f'), key: '', value: '', source: 'placeholder' },
            );
            setFields(normalized);
            fieldsRef.current = normalized;
            const prev = node.getData() as any;
            node.setData(
                {
                    ...prev,
                    __fields: normalized,
                    schema: schemaFromFields(normalized),
                    inputs: mergeInputs(normalized, prev?.inputs),
                },
                { overwrite: true },
            );
        },
        [node],
    );

    const syncRef = useRef(syncFields);
    syncRef.current = syncFields;

    useEffect(() => {
        const d = node.getData() as any;
        if (!d?.__fields) {
            node.setData(
                {
                    ...d,
                    __fields: fields,
                    schema: schemaFromFields(fields),
                    inputs: mergeInputs(fields, d?.inputs),
                },
                { overwrite: true },
            );
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        const onData = () => setData({ ...node.getData() });
        node.on('change:data', onData);
        return () => {
            node.off('change:data', onData);
        };
    }, [node]);

    const updateEdges = useCallback(
        (pid: string) => {
            const graph = node.model?.graph;
            if (!graph) return;
            graph.getConnectedEdges(node).forEach((edge) => {
                const t = edge.getTarget() as any;
                if (t?.port === pid) (graph.findViewByCell(edge) as any)?.update();
            });
        },
        [node],
    );

    // 高度随属性行自动撑开 + 端口 Y 与行对齐（无 ResizeHandle）
    useEffect(() => {
        const height = calcRecordHeight(fields.length);
        const width = Math.max(node.getSize().width || MIN_WIDTH, MIN_WIDTH);
        // 始终写成精确高度，避免历史手动缩放导致端口落在节点外、连不上
        if (
            Math.abs(node.getSize().height - height) > 0.5
            || Math.abs(node.getSize().width - width) > 0.5
        ) {
            node.resize(width, height);
        }

        const outY = height - FOOTER_HEIGHT + FT_RESULT_Y;

        const ensureVarPort = (id: string, y: number) => {
            const existing = node.getPort(id);
            if (existing && existing.group !== 'absolute-in-solid') {
                try {
                    node.setPortProp(id, 'group', 'absolute-in-solid');
                } catch {
                    node.removePort(id);
                }
            }
            if (!node.hasPort(id)) {
                node.addPort({
                    id,
                    group: 'absolute-in-solid',
                    args: { x: 0, y, dx: 0 },
                    zIndex: 1,
                });
            } else if (!dragState) {
                node.setPortProp(id, 'args', { x: 0, y, dx: 0 });
                updateEdges(id);
            }
        };

        if (!node.hasPort('out')) {
            node.addPort({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: width, y: outY, dx: 0 },
                zIndex: 1,
            });
        } else {
            const p = node.getPort('out');
            if (p?.group !== 'absolute-out-solid') {
                node.setPortProp('out', 'group', 'absolute-out-solid');
            }
            node.setPortProp('out', 'args', { x: width, y: outY, dx: 0 });
            updateEdges('out');
        }

        // 历史控制流 in 仍移除；新增总入口 in:payload（增量，不影响 in:var）
        if (node.hasPort('in')) node.removePort('in');
        ensureVarPort('in:payload', RECORD_PAYLOAD_PORT_Y);

        const wanted = new Set<string>(['out', 'in:payload']);
        fields.forEach((f, idx) => {
            if (f.source === 'wire' || f.source === 'placeholder') {
                const pid = `in:var:${f.id}`;
                wanted.add(pid);
                ensureVarPort(pid, fieldPortY(idx));
            }
        });

        node.getPorts().forEach((p) => {
            if (!p.id) return;
            if (p.id.startsWith('in:var:') && !wanted.has(p.id)) node.removePort(p.id);
            if (p.id.startsWith('in:field:')) node.removePort(p.id);
        });
    }, [fields, node, dragState, updateEdges]);

    // ── 连线：in:payload 总入口 / in:var 末行占位升级（原逻辑保留）──
    useEffect(() => {
        let disposed = false;
        let graph: any = null;

        const onConnected = ({ edge, ...evtArgs }: any) => {
            const target = edge.getTarget() as any;
            const targetCellId =
                evtArgs?.currentCell?.id
                || edge?.getTargetCellId?.()
                || (typeof target?.cell === 'string' ? target.cell : target?.cell?.id);
            const targetPort =
                evtArgs?.currentPort
                || edge?.getTargetPortId?.()
                || target?.port;

            if (targetCellId !== node.id) return;
            if (!targetPort) return;

            // 总入口：写入 inputs.payload，供相对路径解析（不改动字段行）
            if (String(targetPort) === 'in:payload') {
                if ((edge as any).__pvPayload) return;
                (edge as any).__pvPayload = true;
                const prev = node.getData() as any;
                node.setData(
                    {
                        ...prev,
                        inputs: {
                            ...(prev?.inputs || {}),
                            payload: { extractPath: '$' },
                        },
                    },
                    { overwrite: true },
                );
                setTimeout(() => {
                    try {
                        edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
                    } catch {
                        /* ignore */
                    }
                    delete (edge as any).__pvPayload;
                }, 50);
                return;
            }

            if (!String(targetPort).startsWith('in:var:')) return;

            const curFields: RecordField[] =
                fieldsRef.current.length
                    ? fieldsRef.current
                    : ((node.getData() as any)?.__fields as RecordField[]) || [];
            const last = curFields[curFields.length - 1];
            if (!last || last.source !== 'placeholder') return;

            const lastPortId = `in:var:${last.id}`;
            if (String(targetPort) !== lastPortId) return;
            if ((edge as any).__pv) return;
            (edge as any).__pv = true;

            const updated = [...curFields];
            const realCount = updated.filter((f) => f.source !== 'placeholder').length;
            updated[updated.length - 1] = {
                ...last,
                key: last.key?.trim() ? last.key : `field${realCount + 1}`,
                value: last.value?.trim() ? last.value : '$',
                source: 'wire',
                valueType: 'string',
            };
            updated.push({
                id: createId('f'),
                key: '',
                value: '',
                source: 'placeholder',
            });
            syncRef.current(updated);

            setTimeout(() => {
                try {
                    edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
                } catch {
                    /* ignore */
                }
                delete (edge as any).__pv;
            }, 50);
        };

        const bind = () => {
            if (disposed) return;
            graph = node.model?.graph;
            if (!graph) {
                requestAnimationFrame(bind);
                return;
            }
            graph.on('edge:connected', onConnected);
        };
        bind();

        return () => {
            disposed = true;
            if (graph) graph.off('edge:connected', onConnected);
        };
    }, [node]);

    // 加载已保存绝对路径时，按入边压回 $ / $.field
    useEffect(() => {
        let cancelled = false;
        const run = () => {
            if (cancelled) return;
            const graph = node.model?.graph;
            if (!graph) {
                requestAnimationFrame(run);
                return;
            }
            const cur: RecordField[] =
                fieldsRef.current.length
                    ? fieldsRef.current
                    : ((node.getData() as any)?.__fields as RecordField[]) || [];
            let changed = false;
            const updated = cur.map((f) => {
                if (f.source !== 'wire') return f;
                const edge = graph.getConnectedEdges(node).find((e: any) => {
                    if (e.getTargetCellId?.() !== node.id) return false;
                    return String(e.getTargetPortId?.()) === `in:var:${f.id}`;
                });
                if (!edge) return f;
                const srcId = edge.getSourceCellId?.();
                const srcPort = edge.getSourcePortId?.() || 'out';
                if (!srcId) return f;
                const next = relativizeExtractPath(f.value, srcId, srcPort);
                if (next === f.value) return f;
                changed = true;
                return { ...f, value: next };
            });
            if (changed) syncRef.current(updated);

            // payload 总入口同样压回 $
            const payloadEdge = graph.getConnectedEdges(node).find((e: any) => {
                if (e.getTargetCellId?.() !== node.id) return false;
                return String(e.getTargetPortId?.()) === 'in:payload';
            });
            if (payloadEdge) {
                const srcId = payloadEdge.getSourceCellId?.();
                const srcPort = payloadEdge.getSourcePortId?.() || 'out';
                if (srcId) {
                    const prev = node.getData() as any;
                    const curPath = prev?.inputs?.payload?.extractPath;
                    const nextPath = relativizeExtractPath(curPath || '$', srcId, srcPort);
                    if (nextPath !== (curPath || '').trim()) {
                        node.setData(
                            {
                                ...prev,
                                inputs: {
                                    ...(prev?.inputs || {}),
                                    payload: { extractPath: nextPath || '$' },
                                },
                            },
                            { overwrite: true },
                        );
                    }
                }
            }
        };
        run();
        return () => { cancelled = true; };
    }, [node]);

    useEffect(() => {
        const graph = node.model?.graph;
        if (!graph) return;
        try {
            graph.getConnectedEdges(node).forEach((edge) => {
                const t = edge.getTarget() as any;
                const tCellId = typeof t?.cell === 'string' ? t.cell : t?.cell?.id;
                if (
                    tCellId === node.id
                    && t?.port?.startsWith('in:var:')
                    && !edge.hasTool('button-remove')
                ) {
                    edge.addTools({ name: 'button-remove', args: { distance: '50%' } });
                }
            });
        } catch {
            /* ignore */
        }
    }, [node, fields]);

    const onAddLiteral = useCallback(
        (valueType: RecordValueType) => {
            const real = fields.filter((f) => f.source !== 'placeholder');
            const ph = fields.find((f) => f.source === 'placeholder');
            syncFields([
                ...real,
                {
                    id: createId('f'),
                    key: `field${real.length + 1}`,
                    value: defaultLiteralValue(valueType),
                    source: 'literal',
                    valueType,
                },
                ph || { id: createId('f'), key: '', value: '', source: 'placeholder' },
            ]);
        },
        [fields, syncFields],
    );

    /** Path：相对 payload 解析的字段（也可再连行级口覆盖为绝对路径） */
    const onAddPath = useCallback(() => {
        const real = fields.filter((f) => f.source !== 'placeholder');
        const ph = fields.find((f) => f.source === 'placeholder');
        const key = `field${real.length + 1}`;
        syncFields([
            ...real,
            {
                id: createId('f'),
                key,
                value: key, // 默认相对路径 = 字段名，可改
                source: 'wire',
                valueType: 'string',
            },
            ph || { id: createId('f'), key: '', value: '', source: 'placeholder' },
        ]);
    }, [fields, syncFields]);

    const onUpdateField = useCallback(
        (id: string, patch: Partial<RecordField>) => {
            syncFields(fields.map((f) => (f.id === id ? { ...f, ...patch } : f)));
        },
        [fields, syncFields],
    );

    const onRemoveField = useCallback(
        (index: number) => {
            const target = fields[index];
            if (!target || target.source === 'placeholder') return;
            const graph = node.model?.graph;
            if (graph && target.source === 'wire') {
                const pid = `in:var:${target.id}`;
                graph.getConnectedEdges(node).forEach((edge) => {
                    const t = edge.getTarget() as any;
                    if (t?.port === pid) graph.removeEdge(edge);
                });
            }
            const next = [...fields];
            next.splice(index, 1);
            syncFields(next);
        },
        [fields, node, syncFields],
    );

    const handleDragStart = useCallback(
        (index: number) => (e: React.MouseEvent) => {
            if (fields[index]?.source === 'placeholder') return;
            e.stopPropagation();
            e.preventDefault();
            setDragState({ index, startY: e.clientY, currentY: e.clientY });
        },
        [fields],
    );

    useEffect(() => {
        if (!dragState) return;
        const { startY } = dragState;
        const maxIdx = Math.max(0, fields.length - 2);

        const onMove = (e: MouseEvent) => {
            e.preventDefault();
            setDragState((p) => (p ? { ...p, currentY: e.clientY } : null));
            const delta = e.clientY - startY;
            const df = fields[dragState.index];
            if (df && (df.source === 'wire' || df.source === 'placeholder')) {
                try {
                    node.setPortProp(`in:var:${df.id}`, 'args', {
                        y: fieldPortY(dragState.index) + delta,
                        x: 0,
                        dx: 0,
                    });
                    updateEdges(`in:var:${df.id}`);
                } catch {
                    /* ignore */
                }
            }
            if (delta > ROW_HEIGHT / 2 && dragState.index < maxIdx) {
                const nv = [...fields];
                [nv[dragState.index], nv[dragState.index + 1]] = [
                    nv[dragState.index + 1],
                    nv[dragState.index],
                ];
                syncFields(nv);
                setDragState({
                    index: dragState.index + 1,
                    startY: startY + ROW_HEIGHT,
                    currentY: e.clientY,
                });
            } else if (delta < -ROW_HEIGHT / 2 && dragState.index > 0) {
                const nv = [...fields];
                [nv[dragState.index], nv[dragState.index - 1]] = [
                    nv[dragState.index - 1],
                    nv[dragState.index],
                ];
                syncFields(nv);
                setDragState({
                    index: dragState.index - 1,
                    startY: startY - ROW_HEIGHT,
                    currentY: e.clientY,
                });
            }
        };

        const onUp = () => {
            fields.forEach((f, i) => {
                if (f.source !== 'wire' && f.source !== 'placeholder') return;
                try {
                    node.setPortProp(`in:var:${f.id}`, 'args', {
                        y: fieldPortY(i),
                        x: 0,
                        dx: 0,
                    });
                    updateEdges(`in:var:${f.id}`);
                } catch {
                    /* ignore */
                }
            });
            setDragState(null);
        };

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
        return () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
        };
    }, [dragState, fields, node, syncFields, updateEdges]);

    const nodeLabel = data?.__label || 'Record';
    const handleTitleChange = useCallback(
        (newTitle: string) => {
            node.setData({ ...node.getData(), __label: newTitle }, { overwrite: true });
        },
        [node],
    );

    const typeMenu = {
        items: TYPE_MENU_ITEMS.map((t) => ({
            key: t.key,
            label: (
                <span style={{ fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    {typeBadge(t.key, { color: '#8c8c8c' })}
                    <span>{t.label}</span>
                </span>
            ),
        })),
        onClick: ({ key }: { key: string }) => {
            if (key === 'path') onAddPath();
            else onAddLiteral(key as RecordValueType);
        },
    };

    const hasPayload = !!(data?.inputs?.payload);

    const valueBoxStyle: React.CSSProperties = {
        flex: 1,
        fontSize: 12,
        height: 32,
        background: '#fff',
        borderRadius: 6,
        border: '1px solid #f0f0f0',
    };

    const renderValueEditor = (field: RecordField) => {
        if (field.source === 'wire') {
            return (
                <Input
                    size="small"
                    placeholder={hasPayload ? '相对路径，如 apiid' : '绝对路径 $.node.out 或先接左上角'}
                    value={field.value === '$' ? '' : field.value}
                    prefix={typeBadge('path')}
                    onChange={(e) => onUpdateField(field.id, { value: e.target.value })}
                    onMouseDown={(e) => e.stopPropagation()}
                    style={valueBoxStyle}
                />
            );
        }

        const vt = field.valueType || 'string';
        if (vt === 'null') {
            return (
                <div
                    style={{
                        ...valueBoxStyle,
                        background: '#fafafa',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '0 11px',
                        color: '#8c8c8c',
                        fontFamily: 'monospace',
                        boxSizing: 'border-box',
                    }}
                >
                    {typeBadge('null')}
                    <span>null</span>
                </div>
            );
        }
        if (vt === 'boolean') {
            return (
                <div
                    style={{
                        ...valueBoxStyle,
                        display: 'flex',
                        alignItems: 'center',
                        paddingLeft: 11,
                        gap: 4,
                        width: '100%',
                        boxSizing: 'border-box',
                    }}
                >
                    {typeBadge('boolean')}
                    <Select
                        size="small"
                        variant="borderless"
                        value={field.value === 'false' ? 'false' : 'true'}
                        options={[
                            { value: 'true', label: 'true' },
                            { value: 'false', label: 'false' },
                        ]}
                        onChange={(v) => onUpdateField(field.id, { value: v })}
                        onMouseDown={(e) => e.stopPropagation()}
                        style={{ flex: 1, fontSize: 12 }}
                    />
                </div>
            );
        }
        if (vt === 'number') {
            return (
                <InputNumber
                    size="small"
                    value={field.value === '' ? undefined : Number(field.value)}
                    prefix={typeBadge('number')}
                    onChange={(v) =>
                        onUpdateField(field.id, { value: v == null ? '' : String(v) })
                    }
                    onMouseDown={(e) => e.stopPropagation()}
                    style={{ ...valueBoxStyle, width: '100%' }}
                    placeholder="0"
                />
            );
        }
        return (
            <Input
                size="small"
                placeholder="Enter text..."
                value={field.value}
                prefix={typeBadge('string')}
                onChange={(e) => onUpdateField(field.id, { value: e.target.value })}
                onMouseDown={(e) => e.stopPropagation()}
                style={valueBoxStyle}
            />
        );
    };

    return (
        <NodeWrapper
            node={node}
            selected={selected}
            themeColor={borderColor}
            outlineCss={outlineCss}
            backgroundColor={themeObj.bodyBg}
        >
            <div style={{ position: 'relative', flexShrink: 0 }}>
                {/* 总入口视觉凸起（in:payload） */}
                <div
                    title={hasPayload ? '已连接总入口 payload' : '总入口：整包传入，字段填相对路径'}
                    style={{
                        position: 'absolute',
                        left: -6,
                        top: '50%',
                        marginTop: -3,
                        width: 6,
                        height: 6,
                        borderRadius: '50%',
                        background: hasPayload ? themeObj.primary : '#bfbfbf',
                        zIndex: 2,
                    }}
                />
                <NodeHeader
                    icon={ICONS.record}
                    title={nodeLabel}
                    theme={themeObj}
                    height={HEADER_HEIGHT}
                    nodeId={node.id}
                    onNodeIdChange={(id) => commitFlowNodeIdChange(node, id)}
                    onTitleChange={handleTitleChange}
                />
            </div>

            <div
                style={{
                    height: fields.length * ROW_HEIGHT + PADDING_Y * 2,
                    padding: `${PADDING_Y}px 8px`,
                    boxSizing: 'border-box',
                    overflow: 'hidden',
                    pointerEvents: 'auto',
                    flexShrink: 0,
                }}
            >
                {fields.map((field, idx) => {
                    const isPlaceholder = field.source === 'placeholder';
                    const isWire = field.source === 'wire';
                    const isDragging = dragState?.index === idx;
                    const isHovering = hoverRowIndex === idx;
                    const transform =
                        isDragging && dragState
                            ? `translateY(${dragState.currentY - dragState.startY}px)`
                            : 'translateY(0)';

                    if (isPlaceholder) {
                        return (
                            <div
                                key={field.id}
                                style={{
                                    height: ROW_HEIGHT,
                                    display: 'flex',
                                    alignItems: 'center',
                                    padding: '0 4px 0 8px',
                                    gap: 8,
                                    position: 'relative',
                                    borderRadius: 6,
                                }}
                            >
                                <div
                                    style={{
                                        position: 'absolute',
                                        left: -6,
                                        top: '50%',
                                        marginTop: -3,
                                        width: 6,
                                        height: 6,
                                        borderRadius: '50%',
                                        background: themeObj.primary,
                                    }}
                                />
                                <Text style={{ fontSize: 12, color: '#bfbfbf', width: 56 }}>
                                    key
                                </Text>
                                <Dropdown
                                    menu={typeMenu}
                                    trigger={['click']}
                                    placement="bottomLeft"
                                >
                                    <div
                                        onClick={(e) => e.stopPropagation()}
                                        onMouseDown={(e) => e.stopPropagation()}
                                        style={{
                                            flex: 1,
                                            height: 32,
                                            borderRadius: 6,
                                            background: '#fafafa',
                                            border: '1px dashed #d9d9d9',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            color: '#1677ff',
                                            fontSize: 18,
                                            lineHeight: 1,
                                            cursor: 'pointer',
                                        }}
                                    >
                                        +
                                    </div>
                                </Dropdown>
                            </div>
                        );
                    }

                    return (
                        <div
                            key={field.id}
                            onMouseEnter={() => !dragState && setHoverRowIndex(idx)}
                            onMouseLeave={() => !dragState && setHoverRowIndex(null)}
                            style={{
                                height: ROW_HEIGHT,
                                display: 'flex',
                                alignItems: 'center',
                                padding: '0 4px 0 4px',
                                gap: 6,
                                transform,
                                zIndex: isDragging ? 100 : 1,
                                position: 'relative',
                                backgroundColor: isDragging
                                    ? '#e6f4ff'
                                    : isHovering
                                        ? '#f5f5f5'
                                        : 'transparent',
                                borderRadius: 6,
                                transition: isDragging ? 'none' : 'background-color 0.15s',
                            }}
                        >
                            {isWire && (
                                <div
                                    style={{
                                        position: 'absolute',
                                        left: -6,
                                        top: '50%',
                                        marginTop: -3,
                                        width: 6,
                                        height: 6,
                                        borderRadius: '50%',
                                        background: themeObj.primary,
                                    }}
                                />
                            )}
                            <div
                                onMouseDown={handleDragStart(idx)}
                                style={{
                                    color: isDragging ? themeObj.primary : '#bfbfbf',
                                    display: 'flex',
                                    cursor: isDragging ? 'grabbing' : 'grab',
                                    padding: 4,
                                }}
                            >
                                {ICONS.drag}
                            </div>
                            <Input
                                size="small"
                                variant="borderless"
                                placeholder="key"
                                value={field.key}
                                onChange={(e) => onUpdateField(field.id, { key: e.target.value })}
                                onMouseDown={(e) => e.stopPropagation()}
                                style={{
                                    width: 72,
                                    fontSize: 12,
                                    color: '#595959',
                                    padding: '0 4px',
                                }}
                            />
                            {renderValueEditor(field)}
                            {isHovering && !dragState && (
                                <DeleteOutlined
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        onRemoveField(idx);
                                    }}
                                    style={{ color: '#ff4d4f', fontSize: 13, padding: '0 4px' }}
                                />
                            )}
                        </div>
                    );
                })}
            </div>

            <div
                style={{
                    height: FOOTER_HEIGHT,
                    position: 'relative',
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'flex-end',
                    padding: '0 12px',
                    borderTop: '1px solid #f0f0f0',
                }}
            >
                <div
                    style={{
                        position: 'absolute',
                        right: 10,
                        top: FT_RESULT_Y,
                        transform: 'translateY(-50%)',
                    }}
                >
                    <Text style={{ fontSize: 12, color: '#595959' }}>Result</Text>
                </div>
            </div>

            <ResizeHandle
                node={node}
                minWidth={MIN_WIDTH}
                minHeight={calcRecordHeight(Math.max(fields.length, 1))}
                axes="x"
                color={themeObj.primary}
                onResize={(nw) => {
                    if (node.hasPort('out')) {
                        const h = node.getSize().height;
                        node.setPortProp('out', 'args', {
                            x: nw,
                            y: h - FOOTER_HEIGHT + FT_RESULT_Y,
                            dx: 0,
                        });
                    }
                }}
            />
        </NodeWrapper>
    );
};

export default RecordNodeComponent;

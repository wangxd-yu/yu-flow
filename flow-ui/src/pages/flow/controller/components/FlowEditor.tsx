// ============================================================================
// FlowEditor.tsx
// V3.2 —— 集成 node-registry + GraphAdapter + 配置面板
// ============================================================================

import React from 'react';
import { useMemoizedFn } from 'ahooks';
import { Alert, message, Modal, Tooltip, Typography } from 'antd';

import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { Graph, Node, Dnd, History, Keyboard, MiniMap, Shape } from '@antv/x6';
import debounce from 'lodash/debounce';
import isEqual from 'lodash/isEqual';
import CodeEditor from './flow-editor/components/CodeEditor';

// V3.2 Modules
import type { FlowDsl, DslNodeType, FlowEditorProps } from './flow-editor/types';
import type { FormInstance } from 'antd';
import {
    exportGraphToDsl,
    importDslToGraph,
    createDefaultDslNode,
    addSingleNodeToGraph,
    updateNodeDslData,
    EDGE_CONFIG,
} from './flow-editor/adapter';
import { validateFlowGraph, validatePortConnection, autoBindLoopNodes } from './flow-editor/validateFlowGraph';
import { initNodeRegistry } from './flow-editor/node-registry';
import { getNodeRegistration } from './flow-editor/node-registry';
import DslPalette from './flow-editor/components/DslPalette';
import QuickAddPopover from './flow-editor/components/QuickAddPopover';
import NodePropertyDrawer from './flow-editor/components/NodePropertyDrawer';
import ActionToolbar from './flow-editor/components/ActionToolbar';
import CanvasToolbar from './flow-editor/components/CanvasToolbar';
import MiniMapPanel from './flow-editor/components/MiniMapPanel';

const { Text } = Typography;

function isFullscreenEnabled() {
    return !!document.fullscreenEnabled;
}

function requestFullscreen(el: HTMLElement) {
    if (el.requestFullscreen) return el.requestFullscreen();
    return Promise.resolve();
}

function exitFullscreen() {
    if (document.exitFullscreen) return document.exitFullscreen();
    return Promise.resolve();
}

// 扩展 Props
export type ExtendedFlowEditorProps = FlowEditorProps & {
    globalForm?: FormInstance;
    isEdit?: boolean;
    onSave?: (script?: string) => any;
    onCancel?: () => void;
    /** Pro 扩展插槽：沉浸式调试器浮层（商业版注入点） */
    addonDebugger?: React.ReactNode;
};

export default function FlowEditor(props: ExtendedFlowEditorProps) {
    const {
        value,
        onChange,
        height = 'calc(100vh - 48px)',
        globalForm,
        isEdit = true,
        onSave,
        onCancel,
        addonDebugger,
    } = props;

    // ── Refs ──
    const rootRef = React.useRef<HTMLDivElement | null>(null);
    const containerRef = React.useRef<HTMLDivElement | null>(null);
    const minimapRef = React.useRef<HTMLDivElement | null>(null);
    const wrapperRef = React.useRef<HTMLDivElement | null>(null);

    const graphRef = React.useRef<Graph | null>(null);
    const dndRef = React.useRef<Dnd | null>(null);
    const historyRef = React.useRef<History | null>(null);

    const lastSyncedScriptRef = React.useRef<string>('');
    const importingRef = React.useRef(false);
    // 用于追踪边刷新任务，防止多次导入时重复调度
    const edgeRefreshTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

    // ── State ──
    const [parseError, setParseError] = React.useState<string | null>(null);
    const [selectedNodeId, setSelectedNodeId] = React.useState<string | null>(null);
    const [minimapVisible, setMinimapVisible] = React.useState(true);
    const [isFullscreen, setIsFullscreen] = React.useState(false);
    const [canUndo, setCanUndo] = React.useState(false);
    const [canRedo, setCanRedo] = React.useState(false);
    const [mode, setMode] = React.useState<'design' | 'code'>('design');
    const [leftPanelCollapsed, setLeftPanelCollapsed] = React.useState(true);
    const [rightPanelCollapsed, setRightPanelCollapsed] = React.useState(true);

    // ── 快捷添加菜单 (Quick Add Menu) ──
    const [quickAddMenu, setQuickAddMenu] = React.useState<{
        visible: boolean;
        x: number;
        y: number;
        sourceEdgeId: string;
        canvasPosition: { x: number; y: number };
        /** 拉线极性：forward=从 out 端口拉出(正向), reverse=从 in 端口拉出(反向) */
        direction: 'forward' | 'reverse';
    } | null>(null);
    // Get accentColor from form values if available, otherwise default
    const currentValues = globalForm?.getFieldsValue() || {};
    const accentColor = currentValues?.uiConfig?.accentColor || '#1677ff';

    // ── Render-phase sync refs ──
    // 仅用于 Graph 初始化 useEffect 中 setTimeout 异步回调，
    // 这些回调在 useEffect 闭包内注册一次，后续 render 不会重新绑定，
    // 因此必须通过 ref 穿透读取最新值。
    const valueRef = React.useRef(value);
    valueRef.current = value;
    const quickAddMenuRef = React.useRef(quickAddMenu);
    quickAddMenuRef.current = quickAddMenu;

    const isGraphReadyRef = React.useRef(false);

    // ============================================================================
    // DSL 导出 → 触发 onChange
    // ============================================================================
    const handleEmitChange = useMemoizedFn((graph: Graph) => {
        if (!onChange) return;
        const dsl = exportGraphToDsl(graph);
        const next = JSON.stringify(dsl, null, 2);
        lastSyncedScriptRef.current = next;
        setParseError(null);
        onChange(next);
    });

    const emitChange = React.useMemo(
        () => debounce(handleEmitChange, 180),
        [handleEmitChange],
    );

    // ============================================================================
    // History 状态更新
    // ============================================================================
    const updateHistoryState = useMemoizedFn(() => {
        const history = historyRef.current;
        if (!history) return;
        setCanUndo(history.canUndo());
        setCanRedo(history.canRedo());
    });

    // ============================================================================
    // 边连接点强制刷新（根本修复：感知 React shape 挂载契约）
    // ============================================================================
    /**
     * 「断开 → 微任务恢复」策略，与 adapter.ts 的 forceEdgeRefresh 保持一致。
     * 迫使 X6 底层重新调用 getPortBodyElement() 查询真实 Port DOM 坐标。
     */
    const refreshAllEdgePorts = useMemoizedFn(() => {
        const graph = graphRef.current;
        if (!graph) return;
        graph.getEdges().forEach((edge) => {
            const src = edge.getSource() as any;
            const tgt = edge.getTarget() as any;
            if (src?.cell && src?.port && tgt?.cell && tgt?.port) {
                edge.setSource({ x: 0, y: 0 });
                edge.setTarget({ x: 0, y: 0 });
                Promise.resolve().then(() => {
                    edge.setSource({ cell: src.cell, port: src.port });
                    edge.setTarget({ cell: tgt.cell, port: tgt.port });
                });
            }
        });
    });

    /**
     * 监听「节点 size 变更」事件来感知 React shape 的首次挂载渲染完成。
     * @antv/x6-react-shape 在 React 组件挂载并完成首次布局后，
     * 会触发 node:change:size（节点根据 React 内容自适应高度）。
     * 此时 Port DOM 已经就绪，可以安全刷新边连接点。
     *
     * 设计：
     *   - 收到的节点放入 renderedSet；
     *   - 当 renderedSet.size >= nodeCount 时（所有节点都已渲染），执行刷新；
     *   - 同时设置 200ms 超时兜底，防止没有 size 变更的纯 SVG 节点漏刷。
     */
    const scheduleEdgeRefresh = useMemoizedFn(
        (graph: Graph, nodeCount: number) => {
            // 清理上一次未完成的刷新任务
            if (edgeRefreshTimerRef.current) {
                clearTimeout(edgeRefreshTimerRef.current);
                edgeRefreshTimerRef.current = null;
            }
            if (nodeCount === 0) return;

            const renderedSet = new Set<string>();

            const onNodeSizeChange = ({ node }: { node: any }) => {
                renderedSet.add(node.id);
                if (renderedSet.size >= nodeCount) {
                    graph.off('node:change:size', onNodeSizeChange);
                    if (edgeRefreshTimerRef.current) {
                        clearTimeout(edgeRefreshTimerRef.current);
                        edgeRefreshTimerRef.current = null;
                    }
                    refreshAllEdgePorts();
                }
            };

            graph.on('node:change:size', onNodeSizeChange);

            edgeRefreshTimerRef.current = setTimeout(() => {
                graph.off('node:change:size', onNodeSizeChange);
                edgeRefreshTimerRef.current = null;
                refreshAllEdgePorts();
            }, 200);
        },
    );

    // ============================================================================
    // DSL 导入 → 重建画布
    // ============================================================================
    const rebuildFromScript = useMemoizedFn(
        (scriptText?: string) => {
            if (!graphRef.current) return;
            const raw = (scriptText || '').trim();
            if (!raw) return;

            let dsl: FlowDsl;
            try {
                const parsed = JSON.parse(raw);
                if (!parsed || typeof parsed !== 'object') {
                    setParseError('JSON 格式不合法');
                    return;
                }
                if (!Array.isArray(parsed.nodes)) parsed.nodes = [];
                if (!Array.isArray(parsed.edges)) parsed.edges = [];
                dsl = parsed as FlowDsl;
            } catch (e: any) {
                setParseError(e?.message || 'JSON 解析失败');
                return;
            }

            lastSyncedScriptRef.current = raw;

            importingRef.current = true;
            // useMemoizedFn 保证直接读取 selectedNodeId 永远是最新值
            const currentSelectedId = selectedNodeId;

            try {
                importDslToGraph(graphRef.current, dsl);

                if (historyRef.current) {
                    historyRef.current.clean();
                    setTimeout(() => {
                        if (historyRef.current) {
                            historyRef.current.options.ignoreChange = false;
                        }
                    }, 50);
                }
                setParseError(null);

                // 恢复选中状态
                if (currentSelectedId && graphRef.current.getCellById(currentSelectedId)) {
                    setSelectedNodeId(currentSelectedId);
                    setTimeout(() => {
                        if (graphRef.current && currentSelectedId) {
                            const node = graphRef.current.getCellById(currentSelectedId) as Node;
                            if (node) {
                                graphRef.current.getNodes().forEach(resetNodeStyle);
                                highlightNode(node);
                            }
                        }
                    }, 10);
                } else {
                    setSelectedNodeId(null);
                }

                updateHistoryState();

                const nodeCount = dsl.nodes.length;
                if (dsl.edges.length > 0 && nodeCount > 0) {
                    setTimeout(() => {
                        if (graphRef.current) {
                            scheduleEdgeRefresh(graphRef.current, nodeCount);
                        }
                    }, 150);
                }
            } finally {
                importingRef.current = false;
            }
        },
    );


    // ============================================================================
    // 节点样式管理
    // ============================================================================
    const resetNodeStyle = useMemoizedFn((n: Node) => {
        const d = n.getData() as any;
        if (!d || !d.__dslType) return;
        if (!getNodeRegistration(d.__dslType as DslNodeType)) return;
        try {
            n.setAttrs({
                body: { ...n.getAttrs()?.body, strokeWidth: 1 },
            });
        } catch { /* React shape 节点无 body attr，忽略 */ }
    });

    const highlightNode = useMemoizedFn((n: Node) => {
        n.setAttrs({
            body: {
                ...n.getAttrs()?.body,
                stroke: accentColor,
                strokeWidth: 3,
            },
        });
    });

    // ============================================================================
    // ResizeObserver
    // ============================================================================
    React.useEffect(() => {
        const el = containerRef.current;
        const wrapperEl = wrapperRef.current;
        if (!el || !wrapperEl) return;

        const handleGlobalMouseDown = (e: MouseEvent) => {
            if (!wrapperRef.current) return;
            if (!wrapperRef.current.contains(e.target as any)) {
                wrapperRef.current.style.borderColor = 'transparent';
                wrapperRef.current.style.borderLeftColor = '#e5e6eb';
                wrapperRef.current.style.borderRightColor = '#e5e6eb';
            }
        };
        document.addEventListener('mousedown', handleGlobalMouseDown);

        let resizeTimer: any = null;
        let hasCentered = false;
        const resize = () => {
            const graph = graphRef.current;
            if (!graph || !wrapperEl) return;
            if (resizeTimer) clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                requestAnimationFrame(() => {
                    const width = wrapperEl.clientWidth;
                    const height = wrapperEl.clientHeight;
                    if (width > 0 && height > 0) {
                        graph.resize(width, height);
                        if (!hasCentered) {
                            hasCentered = true;
                            // 只有当加载已有数据时，才进行自动居中
                            if (value && value.trim()) {
                                graph.centerContent();
                            }
                        }
                    }
                });
            }, 100);
        };

        resize();

        if (typeof ResizeObserver !== 'undefined') {
            const ro = new ResizeObserver(() => resize());
            // 监听 wrapperEl 而不是 containerRef，因为 containerRef 会被 x6 写死宽高
            ro.observe(wrapperEl);
            return () => {
                ro.disconnect();
                if (resizeTimer) clearTimeout(resizeTimer);
                document.removeEventListener('mousedown', handleGlobalMouseDown);
            };
        }

        window.addEventListener('resize', resize);
        return () => {
            window.removeEventListener('resize', resize);
            if (resizeTimer) clearTimeout(resizeTimer);
            document.removeEventListener('mousedown', handleGlobalMouseDown);
        };
    }, [isFullscreen]);

    // ============================================================================
    // Fullscreen
    // ============================================================================
    React.useEffect(() => {
        const onFsChange = () => {
            setIsFullscreen(!!document.fullscreenElement);
            setTimeout(() => {
                if (containerRef.current && graphRef.current) {
                    const w = containerRef.current.clientWidth;
                    const h = containerRef.current.clientHeight;
                    if (w > 0 && h > 0) graphRef.current.resize(w, h);
                }
            }, 100);
        };
        document.addEventListener('fullscreenchange', onFsChange);
        return () => document.removeEventListener('fullscreenchange', onFsChange);
    }, []);

    // ============================================================================
    // Graph 事件监听器 (Extracted to avoid useEffect closure traps)
    // ============================================================================
    const handleKeyboardDelete = useMemoizedFn(() => {
        const sid = selectedNodeId;
        if (sid) {
            const cell = graphRef.current?.getCellById(sid);
            if (cell) {
                const nd = cell.getData?.() as any;
                if (nd?.__dslType === 'request') {
                    message.warning('Request 节点为全局入口，不可删除');
                    return false;
                }
                graphRef.current?.removeCell(cell);
                setSelectedNodeId(null);
            }
        }
        return false;
    });

    const handleNodeClick = useMemoizedFn(({ node }: any) => {
        setSelectedNodeId(node.id);
        graphRef.current?.getNodes().forEach(resetNodeStyle);
        highlightNode(node);
        if (wrapperRef.current) {
            wrapperRef.current.style.borderColor = 'transparent';
            wrapperRef.current.style.borderLeftColor = '#e5e6eb';
            wrapperRef.current.style.borderRightColor = '#e5e6eb';
        }
    });

    const handleBlankMousedown = useMemoizedFn(({ e }: any) => {
        if (containerRef.current && !containerRef.current.contains(e.target as any)) return;
        setSelectedNodeId(null);
        graphRef.current?.getNodes().forEach(resetNodeStyle);
        if (wrapperRef.current) {
            wrapperRef.current.style.borderColor = accentColor;
        }
    });

    const handleCellMousedown = useMemoizedFn(() => {
        if (wrapperRef.current) {
            wrapperRef.current.style.borderColor = 'transparent';
            wrapperRef.current.style.borderLeftColor = '#e5e6eb';
            wrapperRef.current.style.borderRightColor = '#e5e6eb';
        }
    });

    // ============================================================================
    // Graph 初始化
    // ============================================================================
    React.useEffect(() => {
        if (!containerRef.current) return;
        if (graphRef.current) return;

        // V3.2: 初始化节点注册系统（幂等，可安全多次调用）
        initNodeRegistry();

        const graph = new Graph({
            container: containerRef.current,
            background: { color: '#f6f7fb' },
            grid: { size: 10, visible: true },
            panning: { enabled: true },
            mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], factor: 1.1 },
            interacting: {
                edgeMovable: true,
                edgeLabelMovable: false,
                arrowheadMovable: false,
                vertexMovable: false,
                vertexAddable: false,
                vertexDeletable: false,
            },
            highlighting: {
                magnetAdsorbed: {
                    name: 'stroke',
                    args: { attrs: { fill: '#5F95FF', stroke: '#5F95FF' } },
                },
                magnetReject: {
                    name: 'stroke',
                    args: { attrs: { fill: '#ff4d4f', stroke: '#ff4d4f' } },
                },
            },
            connecting: {
                router: EDGE_CONFIG.router,
                connector: EDGE_CONFIG.connector,
                anchor: 'center',
                connectionPoint: 'anchor',
                allowBlank: true,
                snap: { radius: 20 },
                createEdge(this: any, args: any) {
                    const sourcePortId = args?.sourcePort || '';
                    const outputPorts = ['out', 'true', 'false', 'item', 'done', 'default',
                        'headers', 'params', 'body', 'list', 'finish'];
                    const isOutputPort =
                        outputPorts.includes(sourcePortId)
                        || sourcePortId.startsWith('out:')
                        || sourcePortId.startsWith('case_');
                    const direction = (sourcePortId && !isOutputPort) ? 'reverse' : 'forward';

                    return new Shape.Edge({
                        attrs: {
                            ...EDGE_CONFIG.attrs,
                            line: {
                                ...EDGE_CONFIG.attrs.line,
                                strokeDasharray: '5 5', // 拖拽过程中的线全部都是虚线
                                ...(direction === 'reverse' ? { targetMarker: null, sourceMarker: null } : {}),
                            }
                        },
                        zIndex: 0,
                    });
                },
                allowLoop: false,
                allowNode: false,
                highlight: true,
                validateConnection({ sourceCell, targetCell, sourcePort, targetPort }) {
                    if (!sourceCell) return false;
                    // ── 允许连接到空白画布（配合 allowBlank: true，由 QuickAddPopover 处理）──
                    if (!targetCell) return true;
                    if (!sourcePort || !targetPort) return false;

                    // ── 端口类型校验：数据流 vs 控制流互连拦截 ──
                    // ⚠ 注意：validateConnection 在鼠标悬停每个潜在目标时都会调用，
                    //         严禁在此处弹出任何 message/toast，否则会产生大量重复提示。
                    //         仅静默返回 false，X6 的 highlight + 禁止光标已提供足够视觉反馈。
                    const [portTypeOk] = validatePortConnection(sourcePort, targetPort);
                    if (!portTypeOk) return false;


                    // ── 出入端口方向校验 ──
                    // 出端口白名单（源端口合法值）
                    const outputPorts = ['out', 'true', 'false', 'item', 'done', 'default',
                        'headers', 'params', 'body', 'list', 'finish'];// Scatter-Gather 新增
                    // 入端口白名单（目标端口合法值）
                    const inputPorts = ['in', 'start']; // start = For 控制流输入

                    const isSourceOutput =
                        outputPorts.includes(sourcePort)
                        || sourcePort.startsWith('case_')
                        || sourcePort.startsWith('out:');
                    // 同时允许：
                    //   'in'             — 普通控制/数据流入口
                    //   'start'          — For 控制流触发输入
                    //   'in:var:*'       — If/Database 等变量端口
                    //   'in:body'        — Response Body 端口
                    //   'in:*'           — SystemMethod 动态参数端口
                    const isTargetInput = inputPorts.includes(targetPort)
                        || targetPort.startsWith('in:');

                    return isSourceOutput && isTargetInput;
                },
            },
        });

        // History 插件
        const history = new History({ enabled: true, ignoreChange: true });
        graph.use(history);
        historyRef.current = history;

        // Keyboard 插件
        const keyboard = new Keyboard();
        graph.use(keyboard);
        graph.bindKey(['ctrl+z', 'meta+z'], () => {
            if (history.canUndo()) history.undo();
            return false;
        });
        graph.bindKey(['ctrl+shift+z', 'meta+shift+z'], () => {
            if (history.canRedo()) history.redo();
            return false;
        });
        graph.bindKey(['backspace', 'delete'], handleKeyboardDelete);

        // MiniMap 插件
        if (minimapRef.current) {
            const minimap = new MiniMap({
                container: minimapRef.current,
                width: 220,
                height: 140,
                padding: 10,
            });
            graph.use(minimap);
        }

        graphRef.current = graph;
        dndRef.current = new Dnd({ target: graph, scaled: false });

        // ── 事件监听 ──

        graph.on('node:click', handleNodeClick);
        graph.on('blank:mousedown', handleBlankMousedown);
        graph.on('cell:mousedown', handleCellMousedown);

        const schedule = () => {
            if (importingRef.current) return;
            emitChange(graph);
            updateHistoryState();
        };

        graph.on('node:added', schedule);
        graph.on('node:removed', schedule);
        graph.on('edge:added', schedule);
        graph.on('edge:removed', schedule);
        graph.on('node:change:position', schedule);
        graph.on('edge:change:source', schedule);
        graph.on('edge:change:target', schedule);
        graph.on('cell:change:data', schedule);
        graph.on('history:change', () => updateHistoryState());

        // ── 连线交互：点击显示删除按钮，悬停高亮 ──
        graph.on('edge:click', ({ edge }) => {
            // 先清除其他连线的工具
            graph.getEdges().forEach((e) => {
                if (e !== edge) e.removeTools();
            });
            edge.addTools([
                { name: 'button-remove', args: { distance: '50%' } },
            ]);
        });
        graph.on('edge:mouseenter', ({ edge }) => {
            edge.setAttrs({ line: { stroke: '#1677ff', strokeWidth: 2.5 } });
        });
        graph.on('edge:mouseleave', ({ edge }) => {
            edge.setAttrs({ line: { stroke: '#A2B1C3', strokeWidth: 2 } });
        });
        graph.on('blank:click', () => {
            graph.getEdges().forEach((e) => e.removeTools());
            // 关闭快捷添加菜单
            setQuickAddMenu(null);
        });

        // ── 快捷添加：连线到空白画布时弹出组件选择菜单 ──
        // X6 v3 中，edge:connected 对空白目标不一定触发，
        // 因此使用 edge:connected + edge:mouseup 双重检测策略。
        const handleBlankEdge = (edge: any) => {
            const targetCell = edge.getTargetCell();
            // 目标不是节点 → 用户把线拖到了空白画布上
            if (!targetCell) {
                const target = edge.getTarget() as any;
                // 获取悬空端的画布坐标
                const canvasX = target?.x ?? 0;
                const canvasY = target?.y ?? 0;

                // ── 极性判断 (Polarity Detection) ──
                // 拖拽起点的端口 ID 决定拉线方向
                const sourcePortId: string = edge.getSourcePortId?.() || '';
                // 输出端口白名单：从这些端口拉线 → 正向 (forward)
                const outputPortIds = ['out', 'true', 'false', 'item', 'done', 'default',
                    'headers', 'params', 'body', 'list', 'finish'];
                const isOutputPort =
                    outputPortIds.includes(sourcePortId)
                    || sourcePortId.startsWith('out:')
                    || sourcePortId.startsWith('case_');
                // 如果起点是输出端口 → forward；否则（in、in:var:*、start 等输入端口）→ reverse
                const direction: 'forward' | 'reverse' = isOutputPort ? 'forward' : 'reverse';

                // 将虚线样式应用到该边，表示"待确认"
                // 反向拉线时隐藏箭头，避免箭头方向误导（后续 Edge Flipping 会反转方向）
                edge.setAttrs({
                    line: {
                        stroke: '#1677ff',
                        strokeWidth: 2,
                        strokeDasharray: '5 5',
                        ...(direction === 'reverse'
                            ? { targetMarker: null, sourceMarker: null }
                            : {}),
                    },
                });

                // 转换画布坐标 → 页面坐标，用于弹出菜单定位
                const graphContainer = containerRef.current;
                if (graphContainer) {
                    const rect = graphContainer.getBoundingClientRect();
                    const localPoint = graph.localToGraph(canvasX, canvasY);

                    // 美化交互：如果是反向拉线，让弹窗出现在线头左侧（线从右边接入），
                    // 这样视觉上更符合"上游节点在左，线往右连"的空间直觉。
                    // 弹窗定宽约 280px，预留一点间距，整体往左偏移 285px
                    const popoverXOffset = direction === 'reverse' ? -285 : 0;

                    setQuickAddMenu({
                        visible: true,
                        x: localPoint.x + rect.left + popoverXOffset,
                        y: localPoint.y + rect.top,
                        sourceEdgeId: edge.id,
                        canvasPosition: { x: canvasX, y: canvasY },
                        direction,
                    });
                }
            }
        };

        // 方式 1：edge:connected 事件（X6 标准事件，连到节点端口时触发）
        graph.on('edge:connected', ({ edge }) => {
            if (edge.getTargetCell()) {
                // 如果真实连上了某个节点，恢复箭头（因为 createEdge 反向拉线时可能隐藏了箭头）
                edge.setAttrs({
                    line: {
                        ...EDGE_CONFIG.attrs.line,
                        strokeDasharray: '',
                    },
                });
            }
            handleBlankEdge(edge);
        });

        // 方式 2：edge:mouseup 后延迟检测（X6 v3 空白目标可靠兜底）
        graph.on('edge:mouseup', ({ edge }) => {
            setTimeout(() => {
                if (quickAddMenuRef.current?.visible) return;
                const stillExists = graph.getCellById(edge.id);
                if (stillExists) {
                    handleBlankEdge(edge);
                }
            }, 80);
        });

        // Graph 初始化末尾：标记 graph 就绪 + 先建默认画布
        // ── 数据加载统一由 value-sync useEffect 驱动 ──
        // 这里只负责：1) 标记就绪  2) 若 value 已就绪则立即加载  3) 否则先放一个空节点占位
        setTimeout(() => {
            if (graphRef.current && containerRef.current) {
                isGraphReadyRef.current = true;
                const currentValue = valueRef.current;
                if (currentValue && currentValue.trim()) {
                    // value 已就绪（新建场景或数据先于 graph 到达），直接加载
                    rebuildFromScript(currentValue);
                } else {
                    // value 尚未到达（编辑模式表单数据异步加载中）
                    // 创建默认 Request 节点作为视觉占位，不触发 emitChange
                    // ⚠ 必须设置 importingRef 屏蔽 node:added → schedule → emitChange，
                    //    否则 debounced emitChange 会在 180ms 后覆盖真实数据
                    importingRef.current = true;
                    const containerRect = containerRef.current!.getBoundingClientRect();
                    const centerY = Math.max(Math.round(containerRect.height / 2 - 100), 60);
                    const dslNode = createDefaultDslNode('request' as DslNodeType, { x: 80, y: centerY });
                    addSingleNodeToGraph(graphRef.current, dslNode);
                    importingRef.current = false;
                }
                updateHistoryState();
            }
        }, 50);

        return () => {
            importingRef.current = true;
            // 清理未完成的 edge 刷新任务
            if (edgeRefreshTimerRef.current) {
                clearTimeout(edgeRefreshTimerRef.current);
                edgeRefreshTimerRef.current = null;
            }
            graph.off();
            emitChange.cancel();
            graph.dispose();
            graphRef.current = null;
            dndRef.current = null;
            historyRef.current = null;
        };
    }, [emitChange, rebuildFromScript, updateHistoryState]);


    // ── 外部 value 变化 → 重建 ──
    React.useEffect(() => {
        if (!graphRef.current || !isGraphReadyRef.current) return;
        const incoming = (value ?? '').trim();
        const last = (lastSyncedScriptRef.current ?? '').trim();
        if (!incoming) return;
        if (incoming === last) return;

        try {
            const inObj = JSON.parse(incoming);
            const lastObj = JSON.parse(last || '{}');
            if (isEqual(inObj, lastObj)) return;
        } catch {
            // proceed with rebuild
        }

        rebuildFromScript(incoming);
    }, [rebuildFromScript, value]);

    // ============================================================================
    // 当前选中节点
    // ============================================================================
    const selectedNode = React.useMemo(() => {
        if (!graphRef.current || !selectedNodeId) return null;
        return graphRef.current.getCellById(selectedNodeId) as Node | null;
    }, [selectedNodeId]);

    // ============================================================================
    // 单例检查
    // ============================================================================
    const canCreate = useMemoizedFn(
        (type: DslNodeType) => {
            const graph = graphRef.current;
            if (!graph) return { ok: false, reason: '画布未就绪' };

            const config = getNodeRegistration(type);
            if (config?.singleton) {
                const exists = graph.getNodes().some((n) => {
                    const d = n.getData() as any;
                    return d?.__dslType === type;
                });
                if (exists) {
                    // Request 节点使用专属提示
                    const reason = type === 'request'
                        ? '全局只能有一个 Request 节点'
                        : `仅允许一个 ${config.label}`;
                    return { ok: false, reason };
                }
            }

            return { ok: true };
        },
    );

    // ============================================================================
    // 添加节点
    // ============================================================================
    const handleAddNode = useMemoizedFn(
        (type: DslNodeType, position?: { x: number; y: number }) => {
            const graph = graphRef.current;
            if (!graph) return;
            const allow = canCreate(type);
            if (!allow.ok) {
                // Request 节点使用 error 级别提示，其他用 warning
                if (type === 'request') {
                    message.error(allow.reason || '全局只能有一个 Request 节点');
                } else {
                    message.warning(allow.reason || '无法创建该节点');
                }
                return;
            }

            const dslNode = createDefaultDslNode(type, position);

            // 直接向画布中添加单个节点（不清空已有节点和连线）
            const newNode = addSingleNodeToGraph(graph, dslNode);

            setSelectedNodeId(dslNode.id);
            setTimeout(() => {
                if (graphRef.current) {
                    graphRef.current.getNodes().forEach(resetNodeStyle);
                    highlightNode(newNode);
                }
            }, 10);

            emitChange(graph);
            updateHistoryState();
        },
    );

    // ============================================================================
    // 删除节点
    // ============================================================================
    const deleteSelected = useMemoizedFn(() => {
        const graph = graphRef.current;
        if (!graph || !selectedNode) return;
        // ── Request 节点不可删除保护 ──
        const nd = selectedNode.getData?.() as any;
        if (nd?.__dslType === 'request') {
            message.warning('Request 节点为全局入口，不可删除');
            return;
        }
        graph.removeCell(selectedNode);
        setSelectedNodeId(null);
    });

    // ============================================================================
    // 配置面板数据回写
    // ============================================================================
    const handleDataChange = useMemoizedFn(
        (node: Node, data: Record<string, any>) => {
            const graph = graphRef.current;
            if (!graph) return;
            updateNodeDslData(node, data);
            emitChange(graph);
            updateHistoryState();
        },
    );

    // ============================================================================
    // 工具栏回调
    // ============================================================================

    /**
     * 保存前预检 (Pre-flight Validation)
     * 规则 1: For 下游有 Collect 时必须配置 collectStepId（防死锁）
     * 规则 2: Collect 必须有上游 For（孤儿屏障防护）
     * 规则 3: For 无 Collect 下游时柔性警告
     */
    const handleSave = useMemoizedFn(async () => {
        const graph = graphRef.current;
        if (!graph) {
            await onSave?.();
            if (document.fullscreenElement) {
                exitFullscreen();
            }
            return;
        }

        // ── Step 1: 先自动绑定 For → Collect（图拓扑隐式绑定算法） ──
        const bindReport = autoBindLoopNodes(graph);
        if (process.env.NODE_ENV === 'development' && bindReport.length > 0) {
            console.log(
                '[autoBindLoopNodes]',
                bindReport.map(([f, c]) => `${f} -> ${c ?? '(fire-and-forget)'}`).join(', '),
            );
        }

        // ── Step 2: 图拓扑预检 ──
        const result = validateFlowGraph(graph);

        if (!result.canSave) {
            // 阻断保存，展示所有错误
            const errorMessages = result.errors.map((e) => e.message).join('\n\n');
            Modal.error({
                title: '图拓扑校验失败，无法保存',
                content: (
                    <div style={{ whiteSpace: 'pre-wrap', maxHeight: 400, overflowY: 'auto', fontSize: 13, lineHeight: 1.6 }}>
                        {errorMessages}
                    </div>
                ),
                width: 560,
                okText: '知道了，去修改',
            });
            return;
        }

        // 校验通过，提交流程
        const performSave = async () => {
            const dsl = exportGraphToDsl(graph);
            const text = JSON.stringify(dsl, null, 2);
            lastSyncedScriptRef.current = text;
            onChange?.(text);

            await onSave?.(text);

            if (document.fullscreenElement) {
                exitFullscreen();
            }
        };

        // 有 warning 时柔性提示，但不阻断保存
        if (result.warnings.length > 0) {
            const warnMessages = result.warnings.map((w) => w.message).join('\n');
            Modal.confirm({
                title: '图拓扑提示',
                content: (
                    <div style={{ whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6, color: '#d97706' }}>
                        {warnMessages}
                        <div style={{ marginTop: 12, color: '#6b7280', fontSize: 12 }}>
                            是否仍然继续保存？
                        </div>
                    </div>
                ),
                width: 520,
                okText: '确认保存',
                cancelText: '返回修改',
                onOk: performSave,
            });
            return;
        }

        // 无错误无提示，直接保存
        await performSave();
    });
    const handleCopyJson = useMemoizedFn(async () => {
        const graph = graphRef.current;
        if (!graph) return;
        const dsl = exportGraphToDsl(graph);
        const text = JSON.stringify(dsl, null, 2);
        lastSyncedScriptRef.current = text;
        try {
            await navigator.clipboard.writeText(text);
        } catch {
            // ignore
        }
    });

    const onUndo = useMemoizedFn(() => {
        const h = historyRef.current;
        if (!h || !h.canUndo()) return;
        h.undo();
        updateHistoryState();
    });

    const onRedo = useMemoizedFn(() => {
        const h = historyRef.current;
        if (!h || !h.canRedo()) return;
        h.redo();
        updateHistoryState();
    });

    const onToggleFullscreen = useMemoizedFn(() => {
        if (!isFullscreenEnabled()) return;
        const root = rootRef.current;
        if (!root) return;
        if (!document.fullscreenElement) {
            requestFullscreen(root);
        } else {
            exitFullscreen();
        }
    });

    const onReloadFromJson = useMemoizedFn(
        () => rebuildFromScript(value),
    );

    const handleFormat = () => {
        // CodeMirror 没有内置 format action，
        // 对于 JSON 可直接 pretty-print
        if (value && onChange) {
            try {
                const formatted = JSON.stringify(JSON.parse(value), null, 2);
                onChange(formatted);
            } catch {
                // JSON 非法时忽略
            }
        }
    };

    // ============================================================================
    // Render
    // ============================================================================
    return (
        <div
            ref={rootRef}
            style={{
                border: '1px solid #e5e6eb',
                borderRadius: 8,
                overflow: 'hidden',
                width: '100%',
                height: '100%',
                background: '#ffffff',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            <ActionToolbar
                canUndo={canUndo}
                canRedo={canRedo}
                onUndo={onUndo}
                onRedo={onRedo}
                onDelete={deleteSelected}
                minimapVisible={minimapVisible}
                onToggleMinimap={() => setMinimapVisible((v) => !v)}
                isFullscreen={isFullscreen}
                onToggleFullscreen={onToggleFullscreen}
                onReloadFromJson={onReloadFromJson}
                onCopyJson={handleCopyJson}
                mode={mode}
                onModeChange={setMode}
                onSave={handleSave}
                onFormat={handleFormat}
            />

            {parseError && (
                <div style={{ padding: 12, borderBottom: '1px solid #e5e6eb', background: '#fff7e6' }}>
                    <Alert
                        type="warning"
                        showIcon
                        message="校验/解析提示"
                        description={
                            <div style={{ whiteSpace: 'pre-wrap' }}>
                                {parseError}
                                <div style={{ marginTop: 8 }}>
                                    <Text type="secondary">
                                        说明：校验不通过时不会覆盖脚本字段，先修复再保存。
                                    </Text>
                                </div>
                            </div>
                        }
                    />
                </div>
            )}

            <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
                {/* ── 设计模式区域（始终挂载，mode=code 时隐藏） ── */}
                <div style={{ display: mode === 'design' ? 'contents' : 'none' }}>
                    {/* ── 组件面板 (V3.1 Palette) ── */}
                    <div style={{ position: 'relative', display: 'flex', height: '100%', flexShrink: 0 }}>
                        <div
                            style={{
                                width: leftPanelCollapsed ? 0 : 280,
                                minWidth: leftPanelCollapsed ? 0 : 280,
                                height: '100%',
                                overflow: leftPanelCollapsed ? 'hidden' : 'auto',
                                borderRight: leftPanelCollapsed ? 'none' : '1px solid #e5e6eb',
                                background: '#fff',
                                transition: 'width 0.25s ease, min-width 0.25s ease',
                            }}
                        >
                            <DslPalette
                                graphRef={graphRef}
                                onAddNode={handleAddNode}
                                canCreate={canCreate}
                            />
                        </div>
                        {/* 收起/展开按钮 */}
                        <Tooltip title={leftPanelCollapsed ? '展开组件面板' : '收起组件面板'} placement="right">
                            <div
                                onClick={() => setLeftPanelCollapsed((v) => !v)}
                                style={{
                                    position: 'absolute',
                                    right: -16,
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    width: 16,
                                    height: 48,
                                    background: '#fff',
                                    border: '1px solid #e5e6eb',
                                    borderLeft: 'none',
                                    borderRadius: '0 4px 4px 0',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    cursor: 'pointer',
                                    zIndex: 10,
                                    color: '#8c8c8c',
                                    fontSize: 10,
                                    transition: 'color 0.2s, background 0.2s',
                                }}
                                onMouseEnter={(e) => {
                                    (e.currentTarget as HTMLDivElement).style.background = '#f5f5f5';
                                    (e.currentTarget as HTMLDivElement).style.color = '#1677ff';
                                }}
                                onMouseLeave={(e) => {
                                    (e.currentTarget as HTMLDivElement).style.background = '#fff';
                                    (e.currentTarget as HTMLDivElement).style.color = '#8c8c8c';
                                }}
                            >
                                {leftPanelCollapsed ? <RightOutlined /> : <LeftOutlined />}
                            </div>
                        </Tooltip>
                    </div>

                    {/* ── 画布区域 ── */}
                    <div
                        ref={wrapperRef}
                        style={{
                            flex: 1,
                            position: 'relative',
                            height: '100%',
                            overflow: 'hidden',
                            border: '1px solid transparent',
                            borderLeftColor: '#e5e6eb',
                            borderRightColor: '#e5e6eb',
                            transition: 'border-color 0.2s',
                        }}
                    >
                        <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
                        <CanvasToolbar graph={graphRef.current} />
                        <MiniMapPanel visible={minimapVisible} containerRef={minimapRef} />

                        {/* ── 快捷添加弹窗 (Spotlight 风格) ── */}
                        {quickAddMenu?.visible && graphRef.current && (
                            <QuickAddPopover
                                graph={graphRef.current}
                                x={quickAddMenu.x}
                                y={quickAddMenu.y}
                                sourceEdgeId={quickAddMenu.sourceEdgeId}
                                canvasPosition={quickAddMenu.canvasPosition}
                                direction={quickAddMenu.direction}
                                canCreate={canCreate}
                                onNodeCreated={(nodeId, type) => {
                                    // 选中新建节点
                                    setSelectedNodeId(nodeId);
                                    setTimeout(() => {
                                        if (graphRef.current) {
                                            graphRef.current.getNodes().forEach(resetNodeStyle);
                                            const newNode = graphRef.current.getCellById(nodeId) as Node;
                                            if (newNode) highlightNode(newNode);
                                        }
                                    }, 10);
                                    // 触发 DSL 变更
                                    if (graphRef.current) {
                                        emitChange(graphRef.current);
                                        updateHistoryState();
                                    }
                                }}
                                onClose={() => setQuickAddMenu(null)}
                            />
                        )}

                        {/* ── Pro 扩展插槽: 调试器浮层 (addonDebugger) ── */}
                        {addonDebugger}
                    </div>

                    {/* ── 属性配置面板 (V3.1 Property Drawer) ── */}
                    <div
                        style={{
                            position: 'relative',
                            display: 'flex',
                            height: '100%',
                            flexShrink: 0,
                        }}
                    >
                        {/* 收起/展开按钮 */}
                        <Tooltip title={rightPanelCollapsed ? '展开属性面板' : '收起属性面板'} placement="left">
                            <div
                                onClick={() => setRightPanelCollapsed((v) => !v)}
                                style={{
                                    position: 'absolute',
                                    left: -16,
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    width: 16,
                                    height: 48,
                                    background: '#fff',
                                    border: '1px solid #e5e6eb',
                                    borderRight: 'none',
                                    borderRadius: '4px 0 0 4px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    cursor: 'pointer',
                                    zIndex: 10,
                                    color: '#8c8c8c',
                                    fontSize: 10,
                                    transition: 'color 0.2s, background 0.2s',
                                }}
                                onMouseEnter={(e) => {
                                    (e.currentTarget as HTMLDivElement).style.background = '#f5f5f5';
                                    (e.currentTarget as HTMLDivElement).style.color = '#1677ff';
                                }}
                                onMouseLeave={(e) => {
                                    (e.currentTarget as HTMLDivElement).style.background = '#fff';
                                    (e.currentTarget as HTMLDivElement).style.color = '#8c8c8c';
                                }}
                            >
                                {rightPanelCollapsed ? <LeftOutlined /> : <RightOutlined />}
                            </div>
                        </Tooltip>
                        <div
                            style={{
                                width: rightPanelCollapsed ? 0 : 420,
                                minWidth: rightPanelCollapsed ? 0 : 420,
                                height: '100%',
                                overflow: rightPanelCollapsed ? 'hidden' : 'auto',
                                borderLeft: rightPanelCollapsed ? 'none' : '1px solid #e5e6eb',
                                background: '#fff',
                                transition: 'width 0.25s ease, min-width 0.25s ease',
                            }}
                        >
                            <NodePropertyDrawer
                                node={selectedNode}
                                onDataChange={handleDataChange}
                                globalForm={globalForm}
                                isEdit={isEdit}
                            />
                        </div>
                    </div>
                </div>

                {/* ── 代码模式 ── */}
                {mode === 'code' && (
                    <div style={{ flex: 1, height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                        <CodeEditor
                            value={value || ''}
                            onChange={(v) => onChange && onChange(v)}
                            language="json"
                            height="100%"
                            style={{ border: 'none', borderRadius: 0 }}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}

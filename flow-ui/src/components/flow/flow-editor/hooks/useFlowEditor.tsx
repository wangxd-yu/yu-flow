import React from 'react';
import { useMemoizedFn } from 'ahooks';
import { message, Modal } from 'antd';
import { Graph, Node, Dnd, History } from '@antv/x6';
import debounce from 'lodash/debounce';
import isEqual from 'lodash/isEqual';
import type { FlowDsl, DslNodeType, FlowEditorProps } from '../types';
import type { FormInstance } from 'antd';
import { exportGraphToDsl, importDslToGraph, createDefaultDslNode, addSingleNodeToGraph, updateNodeDslData } from '../adapter';
import { validateFlowGraph, autoBindLoopNodes } from '../validateFlowGraph';
import { getNodeRegistration } from '../node-registry';
import { isPaletteNodeAllowed, resolveEditorContext, wrongEntryReason } from '../editorContext';
import { useResizablePanelWidth } from '../components/useResizablePanelWidth';
import { NodeViewModeProvider } from '../node-registry/shared/NodeViewMode';
import { GRAPH_API_METHOD_KEY, methodHasBody } from '../node-registry/nodes/request/RequestNodeComponent';
import { setCurrentDebugContext } from '../components/MacroCompletion';
import type { ExecutionLog } from '../../debugger';
import useFlowGraphInit from './useFlowGraphInit';

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

function isEditing() {
  const active = document.activeElement;
  if (!active) return false;
  const tagName = active.tagName.toLowerCase();
  if (tagName === 'input' || tagName === 'textarea') return true;
  if (active.hasAttribute('contenteditable') || active.closest('[contenteditable="true"]')) return true;
  if (active.classList.contains('cm-content')) return true;
  return false;
}

import type { ExtendedFlowEditorProps, FlowEditorDebugAdapters } from '../types-editor';

export default function useFlowEditor(props: ExtendedFlowEditorProps) {
  const {
    value,
    onChange,
    apiUrl,
    apiMethod,
    apiId,
    apiName,
    height = 'calc(100vh - 48px)',
    globalForm,
    isEdit: propsIsEdit = true,
    onSave,
    onCancel,
    readonlyTrace,
    defaultEntryNode = 'request',
    editorContext: editorContextProp,
    triggerMode = 'http',
    defaultTriggerBody,
    defaultTriggerHeaders,
    defaultTriggerQueryParams,
    contractJson,
    toolbarLeadingExtra,
    debugAdapters,
  } = props;

  const editorContext = React.useMemo(
    () => resolveEditorContext(editorContextProp, defaultEntryNode),
    [editorContextProp, defaultEntryNode],
  );

  const isReadonlySnapshot = !!readonlyTrace;
  const isEdit = isReadonlySnapshot ? false : propsIsEdit;

  const [consoleHeight, setConsoleHeight] = React.useState(0);
  const handleConsoleOpenChange = React.useCallback((open: boolean) => {
    setConsoleHeight(open ? 300 : 0);
  }, []);

  const [breakpoints, setBreakpoints] = React.useState<string[]>([]);

  const rootRef = React.useRef<HTMLDivElement | null>(null);
  const containerRef = React.useRef<HTMLDivElement | null>(null);
  const minimapRef = React.useRef<HTMLDivElement | null>(null);
  const wrapperRef = React.useRef<HTMLDivElement | null>(null);

  const graphRef = React.useRef<Graph | null>(null);
  const [graphInstance, setGraphInstance] = React.useState<Graph | null>(null);
  const dndRef = React.useRef<Dnd | null>(null);
  const historyRef = React.useRef<History | null>(null);

  const lastSyncedScriptRef = React.useRef<string>('');
  const importingRef = React.useRef(false);
  const edgeRefreshTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const [parseError, setParseError] = React.useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = React.useState<string | null>(null);
  const [minimapVisible, setMinimapVisible] = React.useState(true);
  const [isFullscreen, setIsFullscreen] = React.useState(false);
  const [canUndo, setCanUndo] = React.useState(false);
  const [canRedo, setCanRedo] = React.useState(false);
  const [mode, setMode] = React.useState<'design' | 'code'>('design');
  const [leftPanelCollapsed, setLeftPanelCollapsed] = React.useState(true);
  const [rightPanelCollapsed, setRightPanelCollapsed] = React.useState(true);
  const showPropertyPanel = true;
  const { width: rightPanelWidth, onResizeStart: onRightPanelResizeStart } =
    useResizablePanelWidth(rightPanelCollapsed);

  const [executionLogs, setExecutionLogs] = React.useState<ExecutionLog[]>(readonlyTrace?.stepLogs || []);
  const [debuggerSelectedNodeId, setDebuggerSelectedNodeId] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (readonlyTrace?.stepLogs) {
      setExecutionLogs(readonlyTrace.stepLogs);
    }
  }, [readonlyTrace]);

  React.useEffect(() => {
    if (debuggerSelectedNodeId) {
      const log = executionLogs.find(l => l.nodeId === debuggerSelectedNodeId);
      if (log && log.inputs) {
        setCurrentDebugContext(log.inputs);
        return;
      }
    }
    setCurrentDebugContext(null);
  }, [debuggerSelectedNodeId, executionLogs]);

  React.useEffect(() => {
    if (!graphRef.current) return;
    const nodes = graphRef.current.getNodes();
    nodes.forEach(node => {
      node.removeTool('breakpoint-button');
      if (breakpoints.includes(node.id)) {
        node.addTools({
          name: 'button',
          args: {
            markup: [
              {
                tagName: 'circle',
                selector: 'button',
                attrs: {
                  r: 6,
                  fill: '#ff4d4f',
                  stroke: '#fff',
                  strokeWidth: 2,
                  cursor: 'pointer',
                },
              },
            ],
            x: '100%',
            y: 0,
            offset: { x: -8, y: 8 },
            onClick: () => {
              setBreakpoints(prev => prev.filter(id => id !== node.id));
            },
          },
        });
      }
    });
  }, [breakpoints]);

  const [quickAddMenu, setQuickAddMenu] = React.useState<{
    visible: boolean;
    x: number;
    y: number;
    sourceEdgeId: string;
    canvasPosition: { x: number; y: number };
    direction: 'forward' | 'reverse';
  } | null>(null);

  const currentValues = globalForm?.getFieldsValue() || {};
  const accentColor = currentValues?.uiConfig?.accentColor || '#1677ff';

  const valueRef = React.useRef(value);
  valueRef.current = value;
  const quickAddMenuRef = React.useRef(quickAddMenu);
  quickAddMenuRef.current = quickAddMenu;

  const isGraphReadyRef = React.useRef(false);

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

  const updateHistoryState = useMemoizedFn(() => {
    const history = historyRef.current;
    if (!history) return;
    setCanUndo(history.canUndo());
    setCanRedo(history.canRedo());
  });

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

  const scheduleEdgeRefresh = useMemoizedFn(
    (graph: Graph, nodeCount: number) => {
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

  React.useEffect(() => {
    const graph = graphRef.current;
    if (!graph) return;

    graph.getNodes().forEach(n => {
      if (n.id !== selectedNodeId) {
        resetNodeStyle(n);
      } else {
        highlightNode(n);
      }
    });

    executionLogs.forEach(log => {
      const node = graph.getCellById(log.nodeId) as Node;
      if (node) {
        const attrs = node.getAttrs();
        let stroke = '#A2B1C3';
        if (log.status === 'success') stroke = '#52c41a';
        if (log.status === 'error') stroke = '#ff4d4f';
        if (log.status === 'running') stroke = '#1677ff';

        node.setAttrs({
          body: { ...attrs?.body, stroke, strokeWidth: log.status === 'error' ? 3 : 2 }
        });
      }
    });

    if (debuggerSelectedNodeId) {
      const node = graph.getCellById(debuggerSelectedNodeId) as Node;
      if (node) {
        const attrs = node.getAttrs();
        node.setAttrs({
          body: { ...attrs?.body, strokeWidth: 4, strokeDasharray: '5 5' }
        });
        graph.centerCell(node, { padding: 50 });
      }
    }
  }, [executionLogs, debuggerSelectedNodeId, selectedNodeId, resetNodeStyle, highlightNode]);

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

  const handleKeyboardDelete = useMemoizedFn(() => {
    if (isEditing()) return true;
    if (!graphRef.current) return false;
    const cells = graphRef.current.getSelectedCells();
    if (cells.length) {
      cells.forEach(cell => {
        const nd = cell.getData?.() as any;
        if (nd?.__dslType === 'request') {
          message.warning('Request 节点为全局入口，不可删除');
        } else {
          graphRef.current?.removeCell(cell);
        }
      });
      setSelectedNodeId(null);
      return false;
    }

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
    setRightPanelCollapsed(false);
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

  useFlowGraphInit({
    containerRef,
    minimapRef,
    wrapperRef,
    graphRef,
    historyRef,
    dndRef,
    importingRef,
    isGraphReadyRef,
    edgeRefreshTimerRef,
    quickAddMenuRef,
    valueRef,
    isReadonlySnapshot,
    defaultEntryNode,
    handleNodeClick,
    handleBlankMousedown,
    handleCellMousedown,
    handleKeyboardDelete,
    emitChange,
    updateHistoryState,
    rebuildFromScript,
    setGraphInstance,
    setQuickAddMenu,
    setBreakpoints,
    setSelectedNodeId,
  });

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

  React.useEffect(() => {
    const graph = graphRef.current;
    if (!graph || !apiMethod) return;

    const next = String(apiMethod).trim().toUpperCase();
    if (!next) return;

    try {
      (graph as any).set?.(GRAPH_API_METHOD_KEY, next);
    } catch {
      /* ignore */
    }

    let changed = false;
    graph.getNodes().forEach((node) => {
      const d = (node.getData() as any) || {};
      if (d.__dslType !== 'request') return;

      const prev = String(d.method || 'GET').toUpperCase();
      if (prev !== next) {
        node.setData({ ...d, method: next });
        changed = true;
      }

      const needBody = methodHasBody(next);
      if (needBody && !node.hasPort('body')) {
        node.addPort({
          id: 'body',
          group: 'absolute-out-solid',
          args: { x: node.getSize().width || 260, y: 0, dx: 0 },
        });
        changed = true;
      } else if (!needBody && node.hasPort('body')) {
        graph.getConnectedEdges(node).forEach((edge: any) => {
          if (edge.getSourcePortId?.() === 'body' && edge.getSourceCellId?.() === node.id) {
            graph.removeEdge(edge);
          }
        });
        node.removePort('body');
        changed = true;
      }
    });

    if (changed && !importingRef.current) {
      emitChange(graph);
    }
  }, [apiMethod, graphInstance, emitChange]);

  const selectedNode = React.useMemo(() => {
    if (!graphRef.current || !selectedNodeId) return null;
    return graphRef.current.getCellById(selectedNodeId) as Node | null;
  }, [selectedNodeId]);

  const canCreate = useMemoizedFn(
    (type: DslNodeType) => {
      const graph = graphRef.current;
      if (!graph) return { ok: false, reason: '画布未就绪' };

      if (!isPaletteNodeAllowed(type, editorContext)) {
        return { ok: false, reason: wrongEntryReason(type, editorContext) };
      }

      const config = getNodeRegistration(type);
      if (config?.singleton) {
        const exists = graph.getNodes().some((n) => {
          const d = n.getData() as any;
          return d?.__dslType === type;
        });
        if (exists) {
          const reason = type === 'request'
            ? '全局只能有一个 Request 节点'
            : `仅允许一个 ${config.label}`;
          return { ok: false, reason };
        }
      }

      return { ok: true };
    },
  );

  const handleAddNode = useMemoizedFn(
    (type: DslNodeType, position?: { x: number; y: number }) => {
      const graph = graphRef.current;
      if (!graph) return;
      const allow = canCreate(type);
      if (!allow.ok) {
        if (type === 'request') {
          message.error(allow.reason || '全局只能有一个 Request 节点');
        } else {
          message.warning(allow.reason || '无法创建该节点');
        }
        return;
      }

      const dslNode = createDefaultDslNode(type, position);
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

  const deleteSelected = useMemoizedFn(() => {
    const graph = graphRef.current;
    if (!graph || !selectedNode) return;
    const nd = selectedNode.getData?.() as any;
    if (nd?.__dslType === 'request') {
      message.warning('Request 节点为全局入口，不可删除');
      return;
    }
    graph.removeCell(selectedNode);
    setSelectedNodeId(null);
  });

  const handleDataChange = useMemoizedFn(
    (node: Node, data: Record<string, any>) => {
      const graph = graphRef.current;
      if (!graph) return;
      updateNodeDslData(node, data);
      emitChange(graph);
      updateHistoryState();
    },
  );

  const handleSave = useMemoizedFn(async () => {
    const graph = graphRef.current;
    if (!graph) {
      await onSave?.();
      if (document.fullscreenElement) {
        exitFullscreen();
      }
      return;
    }

    const bindReport = autoBindLoopNodes(graph);
    if (process.env.NODE_ENV === 'development' && bindReport.length > 0) {
      console.log(
        '[autoBindLoopNodes]',
        bindReport.map(([f, c]) => `${f} -> ${c ?? '(fire-and-forget)'}`).join(', '),
      );
    }

    const result = validateFlowGraph(graph);

    if (!result.canSave) {
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
    if (value && onChange) {
      try {
        const formatted = JSON.stringify(JSON.parse(value), null, 2);
        onChange(formatted);
      } catch {
        // JSON 非法时忽略
      }
    }
  };

  return {
    value,
    onChange,
    apiUrl,
    apiMethod,
    apiId,
    apiName,
    height,
    globalForm,
    isEdit,
    onSave,
    onCancel,
    readonlyTrace,
    defaultEntryNode,
    editorContext,
    triggerMode,
    defaultTriggerBody,
    defaultTriggerHeaders,
    defaultTriggerQueryParams,
    contractJson,
    toolbarLeadingExtra,
    debugAdapters,
    isReadonlySnapshot,
    consoleHeight,
    handleConsoleOpenChange,
    breakpoints,
    setBreakpoints,
    rootRef,
    containerRef,
    minimapRef,
    wrapperRef,
    graphRef,
    graphInstance,
    dndRef,
    historyRef,
    lastSyncedScriptRef,
    importingRef,
    edgeRefreshTimerRef,
    parseError,
    setParseError,
    selectedNodeId,
    setSelectedNodeId,
    minimapVisible,
    setMinimapVisible,
    isFullscreen,
    setIsFullscreen,
    canUndo,
    canRedo,
    mode,
    setMode,
    leftPanelCollapsed,
    setLeftPanelCollapsed,
    rightPanelCollapsed,
    setRightPanelCollapsed,
    showPropertyPanel,
    rightPanelWidth,
    onRightPanelResizeStart,
    executionLogs,
    setExecutionLogs,
    debuggerSelectedNodeId,
    setDebuggerSelectedNodeId,
    quickAddMenu,
    setQuickAddMenu,
    accentColor,
    valueRef,
    quickAddMenuRef,
    isGraphReadyRef,
    resetNodeStyle,
    highlightNode,
    handleEmitChange,
    emitChange,
    updateHistoryState,
    refreshAllEdgePorts,
    scheduleEdgeRefresh,
    rebuildFromScript,
    handleKeyboardDelete,
    handleNodeClick,
    handleBlankMousedown,
    handleCellMousedown,
    selectedNode,
    canCreate,
    handleAddNode,
    deleteSelected,
    handleDataChange,
    handleSave,
    handleCopyJson,
    onUndo,
    onRedo,
    onToggleFullscreen,
    onReloadFromJson,
    handleFormat,
  };
}

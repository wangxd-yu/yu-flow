import { useEffect, RefObject } from 'react';
import { message } from 'antd';
import { Graph, Node, Dnd, History, Keyboard, MiniMap, Selection, Shape, Snapline, Clipboard } from '@antv/x6';
import type { Dnd as DndType, History as HistoryType, Node as NodeType } from '@antv/x6';
import { initNodeRegistry } from '../node-registry';
import { EDGE_CONFIG, createDefaultDslNode, addSingleNodeToGraph } from '../adapter';
import { validatePortConnection } from '../validateFlowGraph';
import type { DslNodeType } from '../types';

type GraphRef = React.MutableRefObject<Graph | null>;
type HistoryRef = React.MutableRefObject<HistoryType | null>;
type DndRef = React.MutableRefObject<DndType | null>;

function isEditing() {
  const active = document.activeElement;
  if (!active) return false;
  const tagName = active.tagName.toLowerCase();
  if (tagName === 'input' || tagName === 'textarea') return true;
  if (active.hasAttribute('contenteditable') || active.closest('[contenteditable="true"]')) return true;
  if (active.classList.contains('cm-content')) return true;
  return false;
}

export interface UseFlowGraphInitOptions {
  containerRef: RefObject<HTMLDivElement | null>;
  minimapRef: RefObject<HTMLDivElement | null>;
  wrapperRef: RefObject<HTMLDivElement | null>;
  graphRef: GraphRef;
  historyRef: HistoryRef;
  dndRef: DndRef;
  importingRef: React.MutableRefObject<boolean>;
  isGraphReadyRef: React.MutableRefObject<boolean>;
  edgeRefreshTimerRef: React.MutableRefObject<ReturnType<typeof setTimeout> | null>;
  quickAddMenuRef: React.MutableRefObject<{ visible: boolean } | null>;
  valueRef: React.MutableRefObject<string | undefined>;
  isReadonlySnapshot: boolean;
  defaultEntryNode?: DslNodeType;
  handleNodeClick: (args: { node: NodeType }) => void;
  handleBlankMousedown: (args: { e: MouseEvent }) => void;
  handleCellMousedown: () => void;
  handleKeyboardDelete: () => boolean | void;
  emitChange: ((graph: Graph) => void) & { cancel?: () => void };
  updateHistoryState: () => void;
  rebuildFromScript: (script?: string) => void;
  setGraphInstance: (g: Graph | null) => void;
  setQuickAddMenu: (menu: any) => void;
  setBreakpoints: React.Dispatch<React.SetStateAction<string[]>>;
  setSelectedNodeId: React.Dispatch<React.SetStateAction<string | null>>;
}

/**
 * X6 画布初始化 Hook。
 * 负责：节点注册、Graph 实例创建、插件安装、事件绑定、默认节点加载、销毁清理。
 */
export default function useFlowGraphInit(options: UseFlowGraphInitOptions) {
  const {
    containerRef,
    minimapRef,
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
  } = options;

  useEffect(() => {
    if (!containerRef.current) return;
    if (graphRef.current) return;

    initNodeRegistry();

    const graph = new Graph({
      container: containerRef.current,
      background: { color: '#f6f7fb' },
      grid: { size: 10, visible: true },
      panning: isReadonlySnapshot
        ? { enabled: true }
        : { enabled: true, modifiers: 'space' },
      mousewheel: isReadonlySnapshot
        ? { enabled: true, factor: 1.1 }
        : { enabled: true, modifiers: ['ctrl', 'meta'], factor: 1.1 },
      interacting: isReadonlySnapshot
        ? {
            nodeMovable: false,
            edgeMovable: false,
            edgeLabelMovable: false,
            arrowheadMovable: false,
            vertexMovable: false,
            vertexAddable: false,
            vertexDeletable: false,
            magnetConnectable: false,
          }
        : {
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
      },
      connecting: {
        router: EDGE_CONFIG.router,
        connector: EDGE_CONFIG.connector,
        anchor: 'center',
        connectionPoint: 'anchor',
        allowBlank: !isReadonlySnapshot,
        snap: { radius: 20 },
        createEdge(this: any, args: any) {
          const sourcePortId = args?.sourcePort || '';
          const outputPorts = ['out', 'true', 'false', 'item', 'done', 'default',
            'headers', 'params', 'body', 'list', 'finish', 'success', 'fail'];
          const isOutputPort =
            outputPorts.includes(sourcePortId) ||
            sourcePortId.startsWith('out:') ||
            sourcePortId.startsWith('case_');
          const direction = sourcePortId && !isOutputPort ? 'reverse' : 'forward';

          return new Shape.Edge({
            attrs: {
              ...EDGE_CONFIG.attrs,
              line: {
                ...EDGE_CONFIG.attrs.line,
                strokeDasharray: '5 5',
                ...(direction === 'reverse' ? { targetMarker: null, sourceMarker: null } : {}),
              },
            },
            router: EDGE_CONFIG.router,
            connector: EDGE_CONFIG.connector,
            zIndex: EDGE_CONFIG.zIndex,
          });
        },
        allowLoop: false,
        allowNode: false,
        highlight: true,
        validateConnection({ sourceCell, targetCell, sourcePort, targetPort }) {
          if (isReadonlySnapshot) return false;
          if (!sourceCell) return false;
          if (!targetCell) return true;
          if (!sourcePort || !targetPort) return false;

          const [portTypeOk] = validatePortConnection(sourcePort, targetPort);
          if (!portTypeOk) return false;

          const outputPorts = ['out', 'true', 'false', 'item', 'done', 'default',
            'headers', 'params', 'body', 'list', 'finish', 'success', 'fail'];
          const inputPorts = ['in', 'start'];

          const isSourceOutput =
            outputPorts.includes(sourcePort) ||
            sourcePort.startsWith('case_') ||
            sourcePort.startsWith('out:');
          const isTargetInput =
            inputPorts.includes(targetPort) || targetPort.startsWith('in:');

          return isSourceOutput && isTargetInput;
        },
      },
    });

    const history = new History({ enabled: true, ignoreChange: true });
    graph.use(history);
    historyRef.current = history;

    const keyboard = new Keyboard();
    graph.use(keyboard);

    graph.use(new Snapline({ enabled: true, sharp: true }));
    graph.use(
      new Selection({
        enabled: true,
        multiple: !isReadonlySnapshot,
        rubberband: !isReadonlySnapshot,
        movable: !isReadonlySnapshot,
        showNodeSelectionBox: false,
        modifiers: null,
      }),
    );
    graph.use(new Clipboard({ enabled: !isReadonlySnapshot }));

    if (!isReadonlySnapshot) {
      graph.bindKey(['ctrl+z', 'meta+z'], () => {
        if (isEditing()) return true;
        if (history.canUndo()) history.undo();
        return false;
      });
      graph.bindKey(['ctrl+shift+z', 'meta+shift+z'], () => {
        if (isEditing()) return true;
        if (history.canRedo()) history.redo();
        return false;
      });
      graph.bindKey(['ctrl+c', 'meta+c'], () => {
        if (isEditing()) return true;
        const cells = graph.getSelectedCells();
        if (cells.length) {
          graph.copy(cells);
          message.success('已复制');
        }
        return false;
      });
      graph.bindKey(['ctrl+v', 'meta+v'], () => {
        if (isEditing()) return true;
        if (!graph.isClipboardEmpty()) {
          const cells = graph.paste({ offset: 32 });
          graph.cleanSelection();
          graph.select(cells);
          message.success('已粘贴');
        }
        return false;
      });
      graph.bindKey(['backspace', 'delete'], () => handleKeyboardDelete());
    }

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
    setGraphInstance(graph);
    (graph as any).__readonlySnapshot = isReadonlySnapshot;
    dndRef.current = new Dnd({ target: graph, scaled: false });

    graph.on('node:click', handleNodeClick);
    if (!isReadonlySnapshot) {
      graph.on('blank:mousedown', handleBlankMousedown);
      graph.on('cell:mousedown', handleCellMousedown);
    }
    graph.on('node:toggle-breakpoint', ({ node }: any) => {
      setBreakpoints((prev) =>
        prev.includes(node.id) ? prev.filter((id) => id !== node.id) : [...prev, node.id],
      );
    });

    graph.on('node:id-renamed', ({ oldId, newId }: any) => {
      if (!oldId || !newId || oldId === newId) return;
      setBreakpoints((prev) => prev.map((id) => (id === oldId ? newId : id)));
      setSelectedNodeId((prev) => (prev === oldId ? newId : prev));
    });

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

    if (!isReadonlySnapshot) {
      graph.on('edge:click', ({ edge }: any) => {
        graph.getEdges().forEach((e) => {
          if (e !== edge) e.removeTools();
        });
        edge.addTools([{ name: 'button-remove', args: { distance: '50%' } }]);
      });
    }
    graph.on('edge:mouseenter', ({ edge }: any) => {
      edge.setAttrs({ line: { stroke: '#1677ff', strokeWidth: 2.5 } });
    });
    graph.on('edge:mouseleave', ({ edge }: any) => {
      edge.setAttrs({ line: { stroke: '#A2B1C3', strokeWidth: 2 } });
    });
    graph.on('blank:click', () => {
      if (!isReadonlySnapshot) {
        graph.getEdges().forEach((e) => e.removeTools());
      }
      setQuickAddMenu(null);
    });

    const handleBlankEdge = (edge: any) => {
      if (isReadonlySnapshot) return;
      const targetCell = edge.getTargetCell();
      if (!targetCell) {
        const target = edge.getTarget() as any;
        const canvasX = target?.x ?? 0;
        const canvasY = target?.y ?? 0;

        const sourcePortId: string = edge.getSourcePortId?.() || '';
        const outputPortIds = ['out', 'true', 'false', 'item', 'done', 'default',
          'headers', 'params', 'body', 'list', 'finish', 'success', 'fail'];
        const isOutputPort =
          outputPortIds.includes(sourcePortId) ||
          sourcePortId.startsWith('out:') ||
          sourcePortId.startsWith('case_');
        const direction: 'forward' | 'reverse' = isOutputPort ? 'forward' : 'reverse';

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

        const graphContainer = containerRef.current;
        if (graphContainer) {
          const rect = graphContainer.getBoundingClientRect();
          const localPoint = graph.localToGraph(canvasX, canvasY);
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

    graph.on('edge:connected', ({ edge }: any) => {
      if (edge.getTargetCell()) {
        edge.setAttrs({
          line: {
            ...EDGE_CONFIG.attrs.line,
            strokeDasharray: '',
          },
        });
      }
      handleBlankEdge(edge);
    });

    graph.on('edge:mouseup', ({ edge }: any) => {
      setTimeout(() => {
        if (quickAddMenuRef.current?.visible) return;
        const stillExists = graph.getCellById(edge.id);
        if (stillExists) {
          handleBlankEdge(edge);
        }
      }, 80);
    });

    setTimeout(() => {
      if (graphRef.current && containerRef.current) {
        isGraphReadyRef.current = true;
        const currentValue = valueRef.current;
        if (currentValue && currentValue.trim()) {
          rebuildFromScript(currentValue);
        } else {
          importingRef.current = true;
          const containerRect = containerRef.current!.getBoundingClientRect();
          const centerY = Math.max(Math.round(containerRect.height / 2 - 100), 60);
          const entryType = (defaultEntryNode || 'request') as DslNodeType;
          const dslNode = createDefaultDslNode(entryType, { x: 80, y: centerY });
          addSingleNodeToGraph(graphRef.current, dslNode);
          importingRef.current = false;
        }
        updateHistoryState();
      }
    }, 50);

    return () => {
      importingRef.current = true;
      if (edgeRefreshTimerRef.current) {
        clearTimeout(edgeRefreshTimerRef.current);
        edgeRefreshTimerRef.current = null;
      }
      graph.off();
      emitChange.cancel?.();
      graph.dispose();
      graphRef.current = null;
      setGraphInstance(null);
      dndRef.current = null;
      historyRef.current = null;
    };
  }, [
    containerRef,
    minimapRef,
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
  ]);
}

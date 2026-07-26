import React from 'react';
import type { Graph } from '@antv/x6';
import QuickAddPopover from './QuickAddPopover';
import CanvasToolbar from './CanvasToolbar';
import MiniMapPanel from './MiniMapPanel';
import type { DslNodeType } from '../types';

export interface FlowCanvasProps {
  wrapperRef: React.RefObject<HTMLDivElement>;
  containerRef: React.RefObject<HTMLDivElement>;
  minimapRef: React.RefObject<HTMLDivElement>;
  graphRef: React.MutableRefObject<Graph | null>;
  graph: Graph | null;
  consoleHeight: number;
  minimapVisible: boolean;
  quickAddMenu: {
    visible: boolean;
    x: number;
    y: number;
    sourceEdgeId: string;
    canvasPosition: { x: number; y: number };
    direction: 'forward' | 'reverse';
  } | null;
  onCloseQuickAddMenu: () => void;
  onQuickAddNodeCreated: (nodeId: string) => void;
  isReadonlySnapshot: boolean;
  editorContext: 'api' | 'task' | 'service';
  canCreate: (type: DslNodeType) => { ok: boolean; reason?: string };
}

const FlowCanvas: React.FC<FlowCanvasProps> = ({
  wrapperRef,
  containerRef,
  minimapRef,
  graphRef,
  graph,
  consoleHeight,
  minimapVisible,
  quickAddMenu,
  onCloseQuickAddMenu,
  onQuickAddNodeCreated,
  isReadonlySnapshot,
  editorContext,
  canCreate,
}) => {
  return (
    <div
      ref={wrapperRef}
      style={{
        flex: 1,
        position: 'relative',
        height: '100%',
        overflow: 'hidden',
        paddingBottom: consoleHeight,
        transition: 'padding-bottom 0.35s cubic-bezier(0.16,1,0.3,1)',
        border: '1px solid transparent',
        borderLeftColor: '#e5e6eb',
        borderRightColor: '#e5e6eb',
        transitionProperty: 'border-color, padding-bottom',
      }}
    >
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
      <CanvasToolbar graph={graph} />
      <MiniMapPanel visible={minimapVisible} containerRef={minimapRef} />

      {!isReadonlySnapshot && quickAddMenu?.visible && graphRef.current && (
        <QuickAddPopover
          graph={graphRef.current}
          x={quickAddMenu.x}
          y={quickAddMenu.y}
          sourceEdgeId={quickAddMenu.sourceEdgeId}
          canvasPosition={quickAddMenu.canvasPosition}
          direction={quickAddMenu.direction}
          canCreate={canCreate}
          editorContext={editorContext}
          onNodeCreated={onQuickAddNodeCreated}
          onClose={onCloseQuickAddMenu}
        />
      )}
    </div>
  );
};

export default FlowCanvas;

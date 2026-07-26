// ============================================================================
// FlowEditor.tsx
// V3.2 —— 集成 node-registry + GraphAdapter + 配置面板
// ============================================================================

import React from 'react';
import { Alert } from 'antd';
import { Typography } from 'antd';

import CodeEditor from './flow-editor/components/CodeEditor';
import ErrorBoundary from '@/components/ErrorBoundary';

import type { ExtendedFlowEditorProps, FlowEditorDebugAdapters } from './flow-editor/types-editor';
import { exportGraphToDsl } from './flow-editor/adapter';
import FlowNodePropertyPanel from './flow-editor/components/FlowNodePropertyPanel';
import FlowDebugPanel from './flow-editor/components/FlowDebugPanel';
import FlowPalettePanel from './flow-editor/components/FlowPalettePanel';
import FlowCanvas from './flow-editor/components/FlowCanvas';
import ActionToolbar from './flow-editor/components/ActionToolbar';
import useFlowEditor from './flow-editor/hooks/useFlowEditor';
import { NodeViewModeProvider } from './flow-editor/node-registry/shared/NodeViewMode';

const { Text } = Typography;

export type { ExtendedFlowEditorProps, FlowEditorDebugAdapters } from './flow-editor/types-editor';

function FlowEditorInner(props: ExtendedFlowEditorProps) {
    const {
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
        parseError,
        selectedNodeId,
        setSelectedNodeId,
        minimapVisible,
        setMinimapVisible,
        isFullscreen,
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
        resetNodeStyle,
        highlightNode,
        emitChange,
        updateHistoryState,
        handleKeyboardDelete,
        handleNodeClick,
        handleBlankMousedown,
        handleCellMousedown,
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
    } = useFlowEditor(props);

    return (
        <NodeViewModeProvider graph={graphInstance}>
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
                readonly={isReadonlySnapshot}
                leadingExtra={toolbarLeadingExtra}
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
                <div style={{ display: mode === 'design' ? 'contents' : 'none' }}>
                    <FlowPalettePanel
                        visible={!isReadonlySnapshot}
                        collapsed={leftPanelCollapsed}
                        onToggleCollapsed={() => setLeftPanelCollapsed((v) => !v)}
                        graphRef={graphRef}
                        onAddNode={handleAddNode}
                        canCreate={canCreate}
                        editorContext={editorContext}
                    />

                    <FlowCanvas
                        wrapperRef={wrapperRef}
                        containerRef={containerRef}
                        minimapRef={minimapRef}
                        graphRef={graphRef}
                        graph={graphInstance}
                        consoleHeight={consoleHeight}
                        minimapVisible={minimapVisible}
                        quickAddMenu={quickAddMenu}
                        onCloseQuickAddMenu={() => setQuickAddMenu(null)}
                        onQuickAddNodeCreated={(nodeId) => {
                            setSelectedNodeId(nodeId);
                            setTimeout(() => {
                                if (graphRef.current) {
                                    graphRef.current.getNodes().forEach(resetNodeStyle);
                                    const newNode = graphRef.current.getCellById(nodeId) as any;
                                    if (newNode) highlightNode(newNode);
                                }
                            }, 10);
                            if (graphRef.current) {
                                emitChange(graphRef.current);
                                updateHistoryState();
                            }
                        }}
                        isReadonlySnapshot={isReadonlySnapshot}
                        editorContext={editorContext}
                        canCreate={canCreate}
                    />

                    <FlowDebugPanel
                        visible={mode === 'design'}
                        value={value}
                        apiUrl={apiUrl}
                        apiMethod={apiMethod}
                        triggerMode={triggerMode}
                        defaultTriggerBody={defaultTriggerBody}
                        defaultTriggerHeaders={defaultTriggerHeaders}
                        defaultTriggerQueryParams={defaultTriggerQueryParams}
                        contractJson={contractJson}
                        graph={graphRef.current}
                        exportGraphToDsl={exportGraphToDsl}
                        onUndo={onUndo}
                        onRedo={onRedo}
                        canUndo={canUndo}
                        canRedo={canRedo}
                        debugAdapters={debugAdapters}
                        apiId={apiId}
                        apiName={apiName}
                        breakpoints={breakpoints}
                        onConsoleOpenChange={handleConsoleOpenChange}
                        onExecutionLogsChange={setExecutionLogs}
                        onSelectedLogChange={setDebuggerSelectedNodeId}
                        isReadonlySnapshot={isReadonlySnapshot}
                        readonlyTrace={readonlyTrace}
                    />

                    <FlowNodePropertyPanel
                        visible={showPropertyPanel}
                        collapsed={rightPanelCollapsed}
                        onToggleCollapsed={() => setRightPanelCollapsed((v) => !v)}
                        width={rightPanelWidth}
                        onResizeStart={onRightPanelResizeStart}
                        selectedNodeId={selectedNodeId}
                        graph={graphRef.current}
                        globalForm={globalForm}
                        isEdit={isEdit}
                        breakpoints={breakpoints}
                        onToggleBreakpoint={(nodeId) => {
                            setBreakpoints((prev) =>
                                prev.includes(nodeId)
                                    ? prev.filter((id) => id !== nodeId)
                                    : [...prev, nodeId],
                            );
                        }}
                    />
                </div>

                {!isReadonlySnapshot && mode === 'code' && (
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
        </NodeViewModeProvider>
    );
}

export default function FlowEditor(props: ExtendedFlowEditorProps) {
    return (
        <ErrorBoundary name="流程编辑器">
            <FlowEditorInner {...props} />
        </ErrorBoundary>
    );
}

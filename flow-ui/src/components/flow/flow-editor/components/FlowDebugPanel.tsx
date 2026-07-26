import React from 'react';
import { FlowDebugger } from '../../debugger';
import {
  startDebugSession,
  getDebugSessionStatus,
  resumeDebugSession,
  cancelDebugSession,
  debugRunAutoApiConfig,
} from '@/services/flow/flowController';
import { unwrapDebugResult } from '../utils';
import type { FlowEditorDebugAdapters } from '../types-editor';

export interface FlowDebugPanelProps {
  visible: boolean;
  value?: string;
  apiUrl?: string;
  apiMethod?: string;
  triggerMode?: 'http' | 'service';
  defaultTriggerBody?: string;
  defaultTriggerHeaders?: Record<string, string>;
  defaultTriggerQueryParams?: Record<string, string>;
  contractJson?: string;
  graph: { zoom(n: number): void; centerContent(): void; toJSON(): any } | null;
  exportGraphToDsl: (g: any) => any;
  onUndo: () => void;
  onRedo: () => void;
  canUndo: boolean;
  canRedo: boolean;
  debugAdapters?: FlowEditorDebugAdapters;
  apiId?: string;
  apiName?: string;
  breakpoints: string[];
  onConsoleOpenChange: (open: boolean) => void;
  onExecutionLogsChange: (logs: any[]) => void;
  onSelectedLogChange: (nodeId: string | null) => void;
  isReadonlySnapshot: boolean;
  readonlyTrace?: any;
}

const FlowDebugPanel: React.FC<FlowDebugPanelProps> = ({
  visible,
  value,
  apiUrl,
  apiMethod,
  triggerMode,
  defaultTriggerBody,
  defaultTriggerHeaders,
  defaultTriggerQueryParams,
  contractJson,
  graph,
  exportGraphToDsl,
  onUndo,
  onRedo,
  canUndo,
  canRedo,
  debugAdapters,
  apiId,
  apiName,
  breakpoints,
  onConsoleOpenChange,
  onExecutionLogsChange,
  onSelectedLogChange,
  isReadonlySnapshot,
  readonlyTrace,
}) => {
  if (!visible) return null;

  const getCurrentDsl = (payloadDsl: string) =>
    graph ? JSON.stringify(exportGraphToDsl(graph)) : payloadDsl;

  return (
    <FlowDebugger
      dslContent={value}
      apiUrl={apiUrl}
      apiMethod={apiMethod}
      triggerMode={triggerMode}
      defaultTriggerBody={defaultTriggerBody}
      defaultTriggerHeaders={defaultTriggerHeaders}
      defaultTriggerQueryParams={defaultTriggerQueryParams}
      contractJson={contractJson}
      onZoomIn={() => graph?.zoom(0.1)}
      onZoomOut={() => graph?.zoom(-0.1)}
      onFitView={() => graph?.centerContent()}
      onUndo={onUndo}
      onRedo={onRedo}
      canUndo={canUndo}
      canRedo={canRedo}
      onRun={async (payload) => {
        const currentDslStr = getCurrentDsl(payload.dslContent);
        const runPayload = { ...payload, dslContent: currentDslStr };
        if (debugAdapters?.onRun) {
          return unwrapDebugResult(await debugAdapters.onRun(runPayload), 'Run failed');
        }
        const result = await debugRunAutoApiConfig({
          ...runPayload,
          sourceRef: apiId,
          sourceName: apiName,
        });
        return unwrapDebugResult(result, 'Run failed');
      }}
      onDebugStart={
        debugAdapters
          ? debugAdapters.onDebugStart
            ? async (payload) => {
                const currentDslStr = getCurrentDsl(payload.dslContent);
                return unwrapDebugResult(
                  await debugAdapters.onDebugStart!({
                    ...payload,
                    dslContent: currentDslStr,
                  }),
                  'Debug start failed',
                );
              }
            : undefined
          : async (payload) => {
              const currentDslStr = getCurrentDsl(payload.dslContent);
              const result = await startDebugSession({
                ...payload,
                dslContent: currentDslStr,
                sourceRef: apiId,
                sourceName: apiName,
              });
              return unwrapDebugResult(result, 'Debug start failed');
            }
      }
      onDebugStatus={
        debugAdapters
          ? debugAdapters.onDebugStatus
          : async (sessionId, opts) => {
              const res = await getDebugSessionStatus(sessionId, opts);
              return res?.data || res;
            }
      }
      onDebugResume={
        debugAdapters
          ? debugAdapters.onDebugResume
          : async (sessionId, inputs) => {
              await resumeDebugSession(sessionId, inputs || {});
            }
      }
      onDebugCancel={
        debugAdapters
          ? debugAdapters.onDebugCancel
          : async (sessionId) => {
              await cancelDebugSession(sessionId);
            }
      }
      breakpoints={breakpoints}
      onConsoleOpenChange={onConsoleOpenChange}
      onExecutionLogsChange={onExecutionLogsChange}
      onSelectedLogChange={onSelectedLogChange}
      playbackTrace={isReadonlySnapshot ? readonlyTrace : undefined}
    />
  );
};

export default FlowDebugPanel;

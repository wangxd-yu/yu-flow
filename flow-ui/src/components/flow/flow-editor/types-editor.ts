import type { FormInstance } from 'antd';
import type { FlowEditorProps, DslNodeType } from './types';

export type FlowEditorDebugAdapters = {
  onRun: (payload: {
    dslContent: string;
    headers: Record<string, string>;
    queryParams: Record<string, string>;
    body: string;
    contract?: string;
  }) => Promise<any>;
  onDebugStart?: (payload: {
    dslContent: string;
    headers: Record<string, string>;
    queryParams: Record<string, string>;
    body: string;
    breakpoints: string[];
  }) => Promise<{ sessionId: string }>;
  onDebugStatus?: (sessionId: string, opts?: { stepOffset?: number; stepLimit?: number }) => Promise<any>;
  onDebugResume?: (sessionId: string, inputs?: any) => Promise<void>;
  onDebugCancel?: (sessionId: string) => Promise<void>;
};

export type ExtendedFlowEditorProps = FlowEditorProps & {
  globalForm?: FormInstance;
  isEdit?: boolean;
  onSave?: (script?: string) => any;
  onCancel?: () => void;
  onChange?: (dslContent: string) => void;
  apiUrl?: string;
  apiMethod?: string;
  apiId?: string;
  apiName?: string;
  readonlyTrace?: any;
  defaultEntryNode?: DslNodeType;
  editorContext?: 'api' | 'task' | 'service' | 'mq';
  triggerMode?: 'http' | 'service';
  defaultTriggerBody?: string;
  defaultTriggerHeaders?: Record<string, string>;
  defaultTriggerQueryParams?: Record<string, string>;
  contractJson?: string;
  toolbarLeadingExtra?: React.ReactNode;
  debugAdapters?: FlowEditorDebugAdapters;
};

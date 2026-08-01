import type { DslNodeType } from './types';

/** 流程编辑器所属资产上下文 */
export type FlowEditorContext = 'api' | 'task' | 'service' | 'mq';

/** 四类入口节点（互斥） */
export const ENTRY_NODE_TYPES: readonly DslNodeType[] = [
  'request',
  'schedule',
  'service',
  'mqTrigger',
];

export function entryNodeForContext(ctx: FlowEditorContext): DslNodeType {
  switch (ctx) {
    case 'task':
      return 'schedule';
    case 'service':
      return 'service';
    case 'mq':
      return 'mqTrigger';
    default:
      return 'request';
  }
}

/** 非入口节点一律允许；入口仅允许当前上下文对应类型 */
export function isPaletteNodeAllowed(type: DslNodeType, ctx: FlowEditorContext): boolean {
  if (!(ENTRY_NODE_TYPES as readonly string[]).includes(type)) {
    return true;
  }
  return type === entryNodeForContext(ctx);
}

export function resolveEditorContext(
  editorContext?: FlowEditorContext,
  defaultEntryNode?: DslNodeType,
): FlowEditorContext {
  if (editorContext) return editorContext;
  if (defaultEntryNode === 'schedule') return 'task';
  if (defaultEntryNode === 'service') return 'service';
  if (defaultEntryNode === 'mqTrigger') return 'mq';
  return 'api';
}

const ENTRY_LABELS: Partial<Record<DslNodeType, string>> = {
  request: 'Request',
  schedule: 'Schedule',
  service: 'Service',
  mqTrigger: 'MQ Trigger',
};

const CONTEXT_LABELS: Record<FlowEditorContext, string> = {
  api: '接口',
  task: '任务',
  service: '服务',
  mq: 'MQ 任务',
};

export function wrongEntryReason(type: DslNodeType, ctx: FlowEditorContext): string {
  const allowed = entryNodeForContext(ctx);
  const label = ENTRY_LABELS[type] ?? type;
  const allowedLabel = ENTRY_LABELS[allowed] ?? allowed;
  return `当前为${CONTEXT_LABELS[ctx]}编排，不能添加 ${label} 入口（仅允许 ${allowedLabel}）`;
}

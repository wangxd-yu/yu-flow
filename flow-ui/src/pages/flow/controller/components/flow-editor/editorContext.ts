import type { DslNodeType } from '../types';

/** 流程编辑器所属资产上下文 */
export type FlowEditorContext = 'api' | 'task' | 'service';

/** 三类入口节点（互斥） */
export const ENTRY_NODE_TYPES: readonly DslNodeType[] = ['request', 'schedule', 'service'];

export function entryNodeForContext(ctx: FlowEditorContext): DslNodeType {
  switch (ctx) {
    case 'task':
      return 'schedule';
    case 'service':
      return 'service';
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
  return 'api';
}

export function wrongEntryReason(type: DslNodeType, ctx: FlowEditorContext): string {
  const allowed = entryNodeForContext(ctx);
  const label =
    type === 'request' ? 'Request' : type === 'schedule' ? 'Schedule' : 'Service';
  const allowedLabel =
    allowed === 'request' ? 'Request' : allowed === 'schedule' ? 'Schedule' : 'Service';
  return `当前为${ctx === 'api' ? '接口' : ctx === 'task' ? '任务' : '服务'}编排，不能添加 ${label} 入口（仅允许 ${allowedLabel}）`;
}

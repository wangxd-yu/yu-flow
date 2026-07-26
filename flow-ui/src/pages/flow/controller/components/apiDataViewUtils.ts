/**
 * apiDataViewUtils.ts
 * ─────────────────────────────────────────────────────────────────────────────
 * ApiDataViewDrawer 的纯工具函数：字节格式化、契约参数解析、列同步等。
 */
import type { FormInstance } from 'antd/es/form';
import type { ViewExportColumn, FlowController } from '@/services/flow/flowController';

export type ParamField = { name: string; title?: string; section: 'query' | 'body' | 'path' };

export const FALLBACK_LABEL: Record<string, string> = {
  TEMPLATE_MISSING: '未找到模板',
  TEMPLATE_NO_LIST_PLACEHOLDER: '模板缺少 {.字段} 占位符',
  TEMPLATE_FILL_FAILED: '模板填充失败',
};

export function formatBytes(n?: number) {
  if (n == null || Number.isNaN(n)) return '-';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

export function parseContractParams(contractJson?: string): ParamField[] {
  if (!contractJson) return [];
  try {
    const c = typeof contractJson === 'string' ? JSON.parse(contractJson) : contractJson;
    const out: ParamField[] = [];
    const collect = (nodes: any[] | undefined, section: ParamField['section']) => {
      (nodes || []).forEach((n) => {
        if (n?.name) {
          out.push({ name: n.name, title: n.title || n.description, section });
        }
      });
    };
    collect(c?.request?.query, 'query');
    collect(c?.request?.pathParams, 'path');
    collect(c?.request?.body, 'body');
    return out;
  } catch {
    return [];
  }
}

export function syncColumnsFromContract(
  contractJson?: string,
  prev?: ViewExportColumn[],
): ViewExportColumn[] {
  if (!contractJson) return prev?.length ? prev : [];
  try {
    const c = typeof contractJson === 'string' ? JSON.parse(contractJson) : contractJson;
    const body =
      c?.responses?.['200']?.body ||
      c?.responses?.[Object.keys(c?.responses || {})[0]]?.body ||
      [];
    const prevMap = new Map((prev || []).map((x) => [x.field, x]));
    const cols: ViewExportColumn[] = [];
    const walk = (nodes: any[]) => {
      (nodes || []).forEach((n) => {
        if (n?.type === 'array' && n.children?.length) {
          walk(n.children);
          return;
        }
        if (n?.type === 'object' && n.children?.length) {
          walk(n.children);
          return;
        }
        if (n?.name) {
          const old = prevMap.get(n.name);
          cols.push({
            field: n.name,
            header: old?.header || n.title || n.description || n.name,
            exportable: old?.exportable !== false,
            visible: old?.visible !== false,
            templateKey: old?.templateKey || n.name,
            width: old?.width,
          });
        }
      });
    };
    walk(Array.isArray(body) ? body : []);
    return cols;
  } catch {
    return prev?.length ? prev : [];
  }
}

export function buildParamsFromForm(paramForm: FormInstance, paramFields: ParamField[]) {
  const values = paramForm.getFieldsValue();
  const queryParams: Record<string, string> = {};
  const pathParams: Record<string, string> = {};
  const bodyParams: Record<string, any> = {};
  paramFields.forEach((f) => {
    const v = values[`${f.section}__${f.name}`];
    if (v === undefined || v === null || v === '') return;
    if (f.section === 'query') queryParams[f.name] = String(v);
    else if (f.section === 'path') pathParams[f.name] = String(v);
    else bodyParams[f.name] = v;
  });
  return { queryParams, pathParams, bodyParams };
}

import type { SchemaNode } from '../../controller/components/ApiContractDesigner';

/** 内部服务契约（无 HTTP 语义） */
export interface ServiceContract {
  inputs: SchemaNode[];
  outputs: SchemaNode[];
  outputDescription?: string;
}

export const EMPTY_SERVICE_CONTRACT: ServiceContract = {
  inputs: [],
  outputs: [],
  outputDescription: '',
};

export function parseServiceContract(raw?: string | null): ServiceContract {
  if (!raw?.trim()) return { ...EMPTY_SERVICE_CONTRACT, inputs: [], outputs: [] };
  try {
    const obj = JSON.parse(raw);
    return {
      inputs: Array.isArray(obj?.inputs) ? obj.inputs : [],
      outputs: Array.isArray(obj?.outputs) ? obj.outputs : [],
      outputDescription: typeof obj?.outputDescription === 'string' ? obj.outputDescription : '',
    };
  } catch {
    return { ...EMPTY_SERVICE_CONTRACT, inputs: [], outputs: [] };
  }
}

export function stringifyServiceContract(c: ServiceContract): string {
  return JSON.stringify({
    inputs: c.inputs || [],
    outputs: c.outputs || [],
    outputDescription: c.outputDescription || '',
  });
}

/** 扁平化入参名摘要（供入口节点卡片展示） */
export function summarizeInputNames(inputs: SchemaNode[], max = 8): string[] {
  const names: string[] = [];
  const walk = (nodes: SchemaNode[], prefix = '') => {
    for (const n of nodes) {
      const raw = (n.name || '').trim();
      if (!raw || raw === '根节点') {
        if (n.children?.length) walk(n.children, prefix);
        continue;
      }
      const full = prefix ? `${prefix}.${raw}` : raw;
      const hasKids = Array.isArray(n.children) && n.children.length > 0;
      if (n.type === 'object' && hasKids) {
        walk(n.children!, full);
      } else {
        names.push(n.required ? `${full}*` : full);
      }
      if (names.length >= max) return;
    }
  };
  walk(inputs);
  return names;
}

/**
 * 将契约入参摘要写入 DSL 中的 service 入口节点，供画布卡片同步展示。
 * 完整契约仍以资产字段 contract 为准。
 */
export function injectContractIntoServiceDsl(dslContent: string, contract: ServiceContract): string {
  try {
    const dsl = JSON.parse(dslContent || '{"nodes":[],"edges":[]}');
    if (!Array.isArray(dsl.nodes)) return dslContent;
    const summary = summarizeInputNames(contract.inputs);
    const outputSummary = summarizeInputNames(contract.outputs);
    dsl.nodes = dsl.nodes.map((n: any) => {
      if (n?.type !== 'service') return n;
      const nextData = { ...(n.data || {}) };
      // 清理曾写入节点的完整契约，避免 DSL 臃肿
      delete nextData.__serviceContract;
      return {
        ...n,
        data: {
          ...nextData,
          __contractInputs: summary,
          __contractOutputs: outputSummary,
          __outputDescription: contract.outputDescription || '',
        },
      };
    });
    return JSON.stringify(dsl);
  } catch {
    return dslContent;
  }
}

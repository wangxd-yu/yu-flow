import { request } from '@umijs/max';

export type HostCatalogDimension =
  | 'USER_TYPE'
  | 'ROLE'
  | 'PERMISSION'
  | 'DEPT'
  | 'USER';

export interface HostCatalogItem {
  value: string;
  label?: string;
  hint?: string;
  parentId?: string;
  disabled?: boolean;
  source?: 'HOST' | 'FLOW';
}

export interface HostCatalogTreeNode {
  title: string;
  value: string;
  key: string;
  disabled?: boolean;
  children?: HostCatalogTreeNode[];
}

export interface HostCatalogDimensionSlice {
  supported?: boolean;
  searchable?: boolean;
  items?: HostCatalogItem[];
}

export interface HostIdentityCatalog {
  available?: boolean;
  dimensions?: Record<string, HostCatalogDimensionSlice>;
}

const UNAVAILABLE: HostIdentityCatalog = {
  available: false,
  dimensions: {
    USER_TYPE: { supported: false, searchable: false, items: [] },
    ROLE: { supported: false, searchable: false, items: [] },
    PERMISSION: { supported: false, searchable: false, items: [] },
    DEPT: { supported: false, searchable: false, items: [] },
    USER: { supported: false, searchable: false, items: [] },
  },
};

let snapshotPromise: Promise<HostIdentityCatalog> | null = null;
let snapshotExpiresAt = 0;
const SNAPSHOT_TTL_MS = 30_000;

function unwrap<T>(result: any): T {
  if (result?.code === 0 && result.data !== undefined) return result.data as T;
  if (result?.data !== undefined) return result.data as T;
  return result as T;
}

/** 宿主机配置保存后清掉管理端下拉缓存，并通知已打开的策略表单刷新维度显隐。 */
export const HOST_IDENTITY_CATALOG_CHANGED = 'yu-flow:host-identity-catalog-changed';

export function resetHostIdentityCatalogCache() {
  snapshotPromise = null;
  snapshotExpiresAt = 0;
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(HOST_IDENTITY_CATALOG_CHANGED));
  }
}

/** 同一页多个策略块共用请求；30 秒后自动刷新，配置保存时会主动清除。 */
export function getHostIdentityCatalog(
  force = false,
): Promise<HostIdentityCatalog> {
  const settled = snapshotExpiresAt > 0;
  if (
    snapshotPromise &&
    settled &&
    (force || Date.now() >= snapshotExpiresAt)
  ) {
    snapshotPromise = null;
    snapshotExpiresAt = 0;
  }
  if (!snapshotPromise) {
    snapshotPromise = request('/flow-api/host/identity-catalog', {
      method: 'GET',
    })
      .then((res) => {
        const data = unwrap<HostIdentityCatalog>(res);
        if (!data || typeof data !== 'object') {
          return UNAVAILABLE;
        }
        return {
          available: !!data.available,
          dimensions: data.dimensions || UNAVAILABLE.dimensions,
        };
      })
      .catch(() => UNAVAILABLE)
      .finally(() => {
        snapshotExpiresAt = Date.now() + SNAPSHOT_TTL_MS;
      });
  }
  return snapshotPromise;
}

export async function searchHostIdentityCatalog(
  dimension: HostCatalogDimension,
  keyword?: string,
  limit = 50,
): Promise<HostCatalogItem[]> {
  try {
    const res = await request('/flow-api/host/identity-catalog/items', {
      method: 'GET',
      params: { dimension, keyword: keyword || undefined, limit },
    });
    const data = unwrap<HostCatalogItem[]>(res);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export function catalogSlice(
  catalog: HostIdentityCatalog | null | undefined,
  dimension: HostCatalogDimension,
): HostCatalogDimensionSlice {
  return (
    catalog?.dimensions?.[dimension] || {
      supported: false,
      searchable: false,
      items: [],
    }
  );
}

export function catalogItemToOption(item: HostCatalogItem) {
  return {
    label: item.label || item.value,
    value: item.value,
    disabled: !!item.disabled,
    title: item.hint || item.label || item.value,
  };
}

/** 该维度已在宿主身份目录启用（或 SPI supports=true）。 */
export function catalogDimensionEnabled(
  catalog: HostIdentityCatalog | null | undefined,
  dimension: HostCatalogDimension,
): boolean {
  return !!catalogSlice(catalog, dimension).supported;
}

export function catalogSelectItems(
  catalog: HostIdentityCatalog | null | undefined,
  dimension: HostCatalogDimension,
  remoteItems?: HostCatalogItem[],
): HostCatalogItem[] {
  const slice = catalogSlice(catalog, dimension);
  if (!slice.supported) {
    return [];
  }
  if (dimension === 'DEPT' && slice.items?.length) {
    return slice.items;
  }
  if (slice.searchable) {
    return remoteItems || slice.items || [];
  }
  return slice.items || [];
}

export function catalogItemsHaveTree(
  items?: HostCatalogItem[] | null,
): boolean {
  return !!items?.some((i) => String(i?.parentId || '').trim());
}

export function buildCatalogTree(
  items: HostCatalogItem[],
): HostCatalogTreeNode[] {
  const nodes = new Map<string, HostCatalogTreeNode & { parentId?: string }>();
  for (const item of items) {
    if (!item?.value) {
      continue;
    }
    const value = String(item.value).trim();
    const key = value.toLowerCase();
    if (nodes.has(key)) {
      continue;
    }
    nodes.set(key, {
      title: item.label || value,
      value,
      key: value,
      disabled: !!item.disabled,
      parentId: item.parentId ? String(item.parentId).trim() : undefined,
      children: [],
    });
  }
  const roots: HostCatalogTreeNode[] = [];
  nodes.forEach((node) => {
    const parentKey = node.parentId ? node.parentId.toLowerCase() : '';
    const parent =
      parentKey && parentKey !== node.value.toLowerCase()
        ? nodes.get(parentKey)
        : undefined;
    if (parent) {
      parent.children = parent.children || [];
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });
  const prune = (list: HostCatalogTreeNode[]) => {
    for (const n of list) {
      if (n.children?.length) {
        prune(n.children);
      } else {
        delete n.children;
      }
    }
  };
  prune(roots);
  return roots;
}

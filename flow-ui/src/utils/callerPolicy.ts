export type CallerMatchMode = 'ALL' | 'ANY';

export interface CallerPolicy {
  enabled: boolean;
  match: CallerMatchMode;
  userTypes: string[];
  roles: string[];
  permissions: string[];
  deptIds: string[];
  /** 缺省 true：选中上级覆盖下级 */
  deptIncludeChildren?: boolean;
  userIds: string[];
}

export const EMPTY_CALLER_POLICY: CallerPolicy = {
  enabled: false,
  match: 'ALL',
  userTypes: [],
  roles: [],
  permissions: [],
  deptIds: [],
  deptIncludeChildren: true,
  userIds: [],
};

function asStringList(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v
    .map((x) => (x == null ? '' : String(x).trim()))
    .filter(Boolean);
}

export function normalizeCallerPolicy(raw?: Partial<CallerPolicy> | null): CallerPolicy {
  if (!raw) return { ...EMPTY_CALLER_POLICY };
  return {
    enabled: !!raw.enabled,
    match: raw.match === 'ANY' ? 'ANY' : 'ALL',
    userTypes: asStringList(raw.userTypes),
    roles: asStringList(raw.roles),
    permissions: asStringList(raw.permissions),
    deptIds: asStringList(raw.deptIds),
    deptIncludeChildren: raw.deptIncludeChildren !== false,
    userIds: asStringList(raw.userIds),
  };
}

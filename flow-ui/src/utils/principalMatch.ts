import type { CallerMatchMode } from '@/utils/callerPolicy';

export type PrincipalKind = 'ANY_AUTHENTICATED' | 'MATCH' | 'OPEN_APP';

export interface PrincipalMatch {
  name?: string;
  principals: PrincipalKind;
  match?: CallerMatchMode;
  userTypes: string[];
  roles: string[];
  permissions: string[];
  userIds: string[];
  deptIds?: string[];
  deptIncludeChildren?: boolean;
}

export const EMPTY_PRINCIPAL_MATCH: PrincipalMatch = {
  name: '',
  principals: 'MATCH',
  match: 'ALL',
  userTypes: [],
  roles: [],
  permissions: [],
  userIds: [],
  deptIds: [],
  deptIncludeChildren: true,
};

export const MAX_ACCESS_RULES = 8;

export function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item ?? '').trim()).filter(Boolean);
}

export function asPrincipals(raw: unknown): PrincipalKind {
  const value = String(raw || '').toUpperCase();
  if (value === 'MATCH') return 'MATCH';
  if (value === 'OPEN_APP') return 'OPEN_APP';
  if (value === 'ANY_AUTHENTICATED') return 'ANY_AUTHENTICATED';
  return 'MATCH';
}

export function normalizePrincipalMatch(
  raw?: Partial<PrincipalMatch> | null,
): PrincipalMatch {
  return {
    name: String(raw?.name || '').trim(),
    principals: asPrincipals(raw?.principals),
    match: raw?.match === 'ANY' ? 'ANY' : 'ALL',
    userTypes: asStringList(raw?.userTypes),
    roles: asStringList(raw?.roles),
    permissions: asStringList(raw?.permissions),
    userIds: asStringList(raw?.userIds),
    deptIds: asStringList(raw?.deptIds),
    deptIncludeChildren: raw?.deptIncludeChildren !== false,
  };
}

const MATCH_DIM_LISTS: Array<[string, (rule: PrincipalMatch) => string[] | undefined]> = [
  ['USER_TYPE', (rule) => rule.userTypes],
  ['ROLE', (rule) => rule.roles],
  ['PERMISSION', (rule) => rule.permissions],
  ['DEPT', (rule) => rule.deptIds],
  ['USER', (rule) => rule.userIds],
];

/** `enabled` 传入时只统计宿主机配置里已启用、且已填写的维度。 */
export function matchDimensionCount(
  rule: PrincipalMatch,
  enabled?: ReadonlySet<string> | readonly string[] | null,
): number {
  const allow =
    enabled == null ? null : enabled instanceof Set ? enabled : new Set(enabled);
  return MATCH_DIM_LISTS.filter(([key, read]) => {
    if (allow && !allow.has(key)) return false;
    const list = read(rule);
    return Array.isArray(list) && list.length > 0;
  }).length;
}

export function whoLabel(rule: PrincipalMatch): string {
  if (rule.principals === 'ANY_AUTHENTICATED') return '已登录用户';
  if (rule.principals === 'OPEN_APP') {
    return rule.userIds?.length ? `开放应用 ${rule.userIds.join('/')}` : '开放应用';
  }
  return (
    (rule.userTypes || []).join('/') ||
    (rule.roles || []).join('/') ||
    (rule.permissions || []).join('/') ||
    (rule.deptIds || []).join('/') ||
    (rule.userIds || []).join('/') ||
    '指定身份'
  );
}

export type PrivacyEffect = 'MASK' | 'REVEAL';
export type FieldAction = 'REVEAL' | 'MASK' | 'DROP';

export interface PrivacyAccessRule extends PrincipalMatch {
  privacy: PrivacyEffect;
  fields?: Record<string, FieldAction>;
}

export const EMPTY_PRIVACY_RULE: PrivacyAccessRule = {
  ...EMPTY_PRINCIPAL_MATCH,
  name: '',
  principals: 'ANY_AUTHENTICATED',
  privacy: 'MASK',
  fields: {},
};

export function splitFieldNames(raw: unknown): string[] {
  return String(raw ?? '')
    .split(/[,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

/** 把 `createBy,updateBy` 拆成多个键，供字段动作匹配。 */
export function expandFieldActions(
  raw?: Record<string, FieldAction> | null,
): Record<string, FieldAction> {
  const fields: Record<string, FieldAction> = {};
  if (!raw || typeof raw !== 'object') return fields;
  Object.entries(raw).forEach(([key, action]) => {
    const a = String(action || '').toUpperCase();
    if (a !== 'REVEAL' && a !== 'MASK' && a !== 'DROP') return;
    splitFieldNames(key).forEach((name) => {
      fields[name] = a as FieldAction;
    });
  });
  return fields;
}

/** 同一动作的字段收成一行，方便标签录入。 */
export function groupFieldActionRows(
  fields?: Record<string, FieldAction> | null,
): Array<{ names: string[]; action: FieldAction }> {
  const expanded = expandFieldActions(fields);
  const order: FieldAction[] = [];
  const buckets: Partial<Record<FieldAction, string[]>> = {};
  Object.entries(expanded).forEach(([name, action]) => {
    if (!buckets[action]) {
      buckets[action] = [];
      order.push(action);
    }
    buckets[action]!.push(name);
  });
  return order.map((action) => ({ names: buckets[action] || [], action }));
}

export function normalizePrivacyAccessRule(
  raw?: Partial<PrivacyAccessRule> | null,
): PrivacyAccessRule {
  const base = normalizePrincipalMatch(raw);
  return {
    ...base,
    privacy: raw?.privacy === 'REVEAL' ? 'REVEAL' : 'MASK',
    fields: expandFieldActions(raw?.fields),
  };
}

export interface CallerAccessRule extends PrincipalMatch {
  effect?: 'ALLOW';
}

export const EMPTY_CALLER_RULE: CallerAccessRule = {
  ...EMPTY_PRINCIPAL_MATCH,
  name: '',
  principals: 'MATCH',
  effect: 'ALLOW',
};

export function normalizeCallerAccessRule(
  raw?: Partial<CallerAccessRule> | null,
): CallerAccessRule {
  return {
    ...normalizePrincipalMatch(raw),
    effect: 'ALLOW',
  };
}

export function hasLegacyCallerConstraint(cp: {
  userTypes?: unknown;
  roles?: unknown;
  permissions?: unknown;
  deptIds?: unknown;
  userIds?: unknown;
} | null | undefined): boolean {
  if (!cp) return false;
  return [
    cp.userTypes,
    cp.roles,
    cp.permissions,
    cp.deptIds,
    cp.userIds,
  ].some((list) => Array.isArray(list) && list.some((x) => String(x || '').trim()));
}

export function parseCallerAccessRules(cp: any): CallerAccessRule[] {
  if (Array.isArray(cp?.rules) && cp.rules.length) {
    return cp.rules.map((rule: CallerAccessRule) => normalizeCallerAccessRule(rule));
  }
  if (!hasLegacyCallerConstraint(cp)) return [];
  return [
    normalizeCallerAccessRule({
      name: '调用方',
      principals: 'MATCH',
      match: cp?.match,
      userTypes: cp?.userTypes,
      roles: cp?.roles,
      permissions: cp?.permissions,
      deptIds: cp?.deptIds,
      deptIncludeChildren: cp?.deptIncludeChildren,
      userIds: cp?.userIds,
      effect: 'ALLOW',
    }),
  ];
}

export function privacyEffectLabel(privacy: PrivacyEffect): string {
  return privacy === 'REVEAL' ? '明文' : '脱敏';
}

export function previewAccessControl(input: {
  callerEnabled?: boolean;
  callerRules?: CallerAccessRule[];
  privacyEnabled?: boolean;
  privacyRules?: PrivacyAccessRule[];
}): string {
  const callerOn = !!input.callerEnabled;
  const callers = (input.callerRules || []).map(normalizeCallerAccessRule);
  const privacyOn = input.privacyEnabled !== false;
  const privacy = (input.privacyRules || []).map(normalizePrivacyAccessRule);

  const ingress = !callerOn
    ? '不限制谁可以调用'
    : callers.length
      ? `可调用：${callers.map((r) => whoLabel(r)).join('、')}`
      : '已启用调用方策略但未配置允许行（仅要求能解析到主体）';

  if (!privacyOn) {
    return `${ingress}。出站隐私未启用。`;
  }
  if (!privacy.length) {
    return `${ingress}；未命中隐私规则一律脱敏。`;
  }
  const out = privacy.map((r) => {
    const fieldBits = Object.entries(r.fields || {})
      .map(([k, a]) => `${k}=${a === 'DROP' ? '去掉' : a === 'REVEAL' ? '明文' : '脱敏'}`)
      .join('，');
    return fieldBits
      ? `${whoLabel(r)}：${privacyEffectLabel(r.privacy)}（${fieldBits}）`
      : `${whoLabel(r)}：${privacyEffectLabel(r.privacy)}`;
  });
  return `${ingress}；${out.join('；')}；未命中脱敏。`;
}

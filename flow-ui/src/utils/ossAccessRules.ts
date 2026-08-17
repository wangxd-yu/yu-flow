import type { CallerMatchMode } from '@/utils/callerPolicy';

export type OssDownloadScope = 'OFF' | 'SELF' | 'DEPT' | 'ALL';
export type OssPrincipals = 'ANY_AUTHENTICATED' | 'MATCH' | 'OPEN_APP';

export interface OssAccessRule {
  name?: string;
  principals: OssPrincipals;
  match?: CallerMatchMode;
  userTypes: string[];
  roles: string[];
  permissions: string[];
  userIds: string[];
  upload: boolean;
  downloadScope: OssDownloadScope;
}

export const EMPTY_ACCESS_RULE: OssAccessRule = {
  name: '',
  principals: 'ANY_AUTHENTICATED',
  match: 'ALL',
  userTypes: [],
  roles: [],
  permissions: [],
  userIds: [],
  upload: true,
  downloadScope: 'SELF',
};

export const MAX_OSS_ACCESS_RULES = 8;

export type OssAccessPresetKey =
  | 'personal'
  | 'userOps'
  | 'dept'
  | 'opsPublish'
  | 'opsOnly'
  | 'openApp'
  | 'anonymous';

export interface OssAccessPreset {
  key: OssAccessPresetKey;
  label: string;
  hint: string;
  requireAuth: boolean;
}

export const OSS_ACCESS_PRESETS: OssAccessPreset[] = [
  { key: 'personal', label: '个人文件', hint: '已登录可传，下载仅本人', requireAuth: true },
  { key: 'userOps', label: '用户 + 运营', hint: '用户仅本人；运营看全部', requireAuth: true },
  { key: 'dept', label: '部门资料', hint: '已登录看本部门；运营看全部', requireAuth: true },
  { key: 'opsPublish', label: '运营上传 · 全员下载', hint: '仅运营可传，已登录都能看', requireAuth: true },
  { key: 'opsOnly', label: '仅运营内部', hint: '只有运营可传可看', requireAuth: true },
  { key: 'openApp', label: '开放应用代传', hint: 'AppKey 可传，运营看全部', requireAuth: true },
  { key: 'anonymous', label: '匿名征集', hint: '游客可传；登录后看全部', requireAuth: false },
];

function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item ?? '').trim()).filter(Boolean);
}

function asPrincipals(raw: unknown): OssPrincipals {
  const value = String(raw || '').toUpperCase();
  if (value === 'MATCH') return 'MATCH';
  if (value === 'OPEN_APP') return 'OPEN_APP';
  return 'ANY_AUTHENTICATED';
}

export function normalizeAccessRule(raw?: Partial<OssAccessRule> | null): OssAccessRule {
  const scope = String(raw?.downloadScope || 'OFF').toUpperCase();
  const downloadScope: OssDownloadScope =
    scope === 'SELF' || scope === 'DEPT' || scope === 'ALL' ? scope : 'OFF';
  return {
    name: String(raw?.name || '').trim(),
    principals: asPrincipals(raw?.principals),
    match: raw?.match === 'ANY' ? 'ANY' : 'ALL',
    userTypes: asStringList(raw?.userTypes),
    roles: asStringList(raw?.roles),
    permissions: asStringList(raw?.permissions),
    userIds: asStringList(raw?.userIds),
    upload: !!raw?.upload,
    downloadScope,
  };
}

function fromLegacyPolicy(
  name: string,
  policy: any,
  upload: boolean,
  downloadScope: OssDownloadScope,
): OssAccessRule {
  return normalizeAccessRule({
    name,
    principals: 'MATCH',
    match: policy?.match,
    userTypes: policy?.userTypes,
    roles: policy?.roles,
    permissions: policy?.permissions,
    userIds: policy?.userIds,
    upload,
    downloadScope,
  });
}

export function parseOssAccessRules(raw?: string | { rules?: OssAccessRule[] } | null): OssAccessRule[] {
  if (!raw) return [];
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (Array.isArray(obj?.rules)) {
      return obj.rules.map((rule: OssAccessRule) => normalizeAccessRule(rule));
    }
    const rules: OssAccessRule[] = [];
    if (obj?.upload?.enabled) {
      rules.push(fromLegacyPolicy('上传', obj.upload, true, 'OFF'));
    }
    if (obj?.download?.enabled) {
      rules.push(fromLegacyPolicy('下载', obj.download, false, 'SELF'));
    }
    return rules;
  } catch {
    return [];
  }
}

export function buildOssAccessRulesJson(rules: OssAccessRule[]): string {
  return JSON.stringify({
    rules: (rules || []).map((rule) => normalizeAccessRule(rule)),
  });
}

const END_USER_LIKE = /^(END_USER|USER|CUSTOMER|MEMBER|C_USER)$/i;
const OPEN_APP_LIKE = /^(OPEN_APP|OPENAPP|APP)$/i;
const OPS_CODE = /^(ADMIN|STAFF|OPERATOR|OPS|PLATFORM_OPS|PLATFORM_ADMIN)$/i;
const OPS_TEXT = /运营|管理|客服/;

export function guessOpsUserTypes(
  items: Array<{ value?: string | number; label?: string }>,
): string[] {
  const rows = (items || []).map((item) => ({
    value: String(item?.value ?? '').trim(),
    label: String(item?.label ?? ''),
  })).filter((row) => row.value && !OPEN_APP_LIKE.test(row.value) && !END_USER_LIKE.test(row.value));
  const preferred = rows.filter(
    (row) => OPS_CODE.test(row.value) || OPS_TEXT.test(row.value) || OPS_TEXT.test(row.label),
  );
  const picked = (preferred.length ? preferred : rows).map((row) => row.value);
  return picked.length ? Array.from(new Set(picked)) : ['STAFF'];
}

function loginRule(scope: OssDownloadScope, upload = true): OssAccessRule {
  return {
    ...EMPTY_ACCESS_RULE,
    name: '已登录用户',
    principals: 'ANY_AUTHENTICATED',
    upload,
    downloadScope: scope,
  };
}

function opsRule(opsUserTypes: string[], upload: boolean, scope: OssDownloadScope): OssAccessRule {
  return {
    ...EMPTY_ACCESS_RULE,
    name: '运营',
    principals: 'MATCH',
    userTypes: opsUserTypes,
    upload,
    downloadScope: scope,
  };
}

export function personalFilesPreset(): OssAccessRule[] {
  return [loginRule('SELF', true)];
}

export function userAndOpsPreset(opsUserTypes: string[]): OssAccessRule[] {
  return [loginRule('SELF', true), opsRule(opsUserTypes, false, 'ALL')];
}

export function deptSpacePreset(opsUserTypes: string[]): OssAccessRule[] {
  return [loginRule('DEPT', true), opsRule(opsUserTypes, false, 'ALL')];
}

export function opsPublishPreset(opsUserTypes: string[]): OssAccessRule[] {
  return [opsRule(opsUserTypes, true, 'ALL'), loginRule('ALL', false)];
}

export function opsOnlyPreset(opsUserTypes: string[]): OssAccessRule[] {
  return [opsRule(opsUserTypes, true, 'ALL')];
}

export function openAppUploadPreset(opsUserTypes: string[]): OssAccessRule[] {
  return [
    {
      ...EMPTY_ACCESS_RULE,
      name: '开放应用',
      principals: 'OPEN_APP',
      upload: true,
      downloadScope: 'OFF',
    },
    opsRule(opsUserTypes, false, 'ALL'),
  ];
}

export function anonymousCollectPreset(): OssAccessRule[] {
  return [loginRule('ALL', true)];
}

export function buildOssAccessPreset(key: OssAccessPresetKey, opsUserTypes: string[]): OssAccessRule[] {
  switch (key) {
    case 'userOps':
      return userAndOpsPreset(opsUserTypes);
    case 'dept':
      return deptSpacePreset(opsUserTypes);
    case 'opsPublish':
      return opsPublishPreset(opsUserTypes);
    case 'opsOnly':
      return opsOnlyPreset(opsUserTypes);
    case 'openApp':
      return openAppUploadPreset(opsUserTypes);
    case 'anonymous':
      return anonymousCollectPreset();
    case 'personal':
    default:
      return personalFilesPreset();
  }
}

function whoLabel(rule: OssAccessRule): string {
  if (rule.principals === 'ANY_AUTHENTICATED') return '已登录用户';
  if (rule.principals === 'OPEN_APP') {
    return rule.userIds.length ? `开放应用 ${rule.userIds.join('/')}` : '开放应用';
  }
  return (
    rule.userTypes.join('/') ||
    rule.roles.join('/') ||
    rule.permissions.join('/') ||
    rule.userIds.join('/') ||
    '指定身份'
  );
}

function scopeLabel(scope: OssDownloadScope): string {
  if (scope === 'ALL') return '看全部';
  if (scope === 'DEPT') return '看本部门';
  if (scope === 'SELF') return '仅本人';
  return '不可下载';
}

export function previewAccessRules(
  rules: OssAccessRule[],
  visibility: string,
): { type: 'info' | 'warning'; text: string } {
  if (visibility === 'PUBLIC') {
    return { type: 'info', text: '公有文件任何人可读；下面的规则只约束谁能上传。' };
  }
  const list = (rules || []).map((rule) => normalizeAccessRule(rule));
  if (!list.length) {
    return { type: 'warning', text: '私有场景尚未配置规则，宿主用户将无法上传或下载。' };
  }
  const parts = list.map(
    (rule) => `${whoLabel(rule)}：${rule.upload ? '可上传' : '不可上传'}，${scopeLabel(rule.downloadScope)}`,
  );
  const uploadNoDownload = list.some((rule) => rule.upload && rule.downloadScope === 'OFF');
  return {
    type: uploadNoDownload ? 'warning' : 'info',
    text: uploadNoDownload
      ? `${parts.join('；')}。有人能上传但不能下载，确认是否故意如此。`
      : parts.join('；'),
  };
}

export function matchDimensionCount(rule: OssAccessRule): number {
  return [rule.userTypes, rule.roles, rule.permissions, rule.userIds].filter(
    (list) => Array.isArray(list) && list.length > 0,
  ).length;
}

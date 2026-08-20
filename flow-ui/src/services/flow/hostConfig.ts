import type { CallerAccessRule, PrivacyAccessRule } from '@/utils/principalMatch';
import { request } from '@umijs/max';
import type { HostCatalogDimension, HostCatalogItem } from './hostIdentityCatalog';

export interface HostCatalogDimBinding {
  enabled?: boolean;
  valueField?: string;
  labelField?: string;
  parentField?: string;
  searchable?: boolean;
}

export interface HostCatalogApiMeta {
  id: string;
  name: string;
  url: string;
  serviceType?: string;
  publishStatus?: number;
  hasUnpublishedChanges?: boolean;
}

export interface HostCatalogOverview {
  spiOverride?: boolean;
  settings?: Record<string, HostCatalogDimBinding>;
  apis?: Record<string, HostCatalogApiMeta>;
}

function unwrap<T>(result: any): T {
  if (result?.code === 0 && result.data !== undefined) return result.data as T;
  if (result?.data !== undefined) return result.data as T;
  return result as T;
}

export async function getHostCatalogOverview(): Promise<HostCatalogOverview> {
  const res = await request('/flow-api/host/config', { method: 'GET' });
  return unwrap<HostCatalogOverview>(res) || {};
}

export async function saveHostCatalogSettings(
  settings: Record<string, HostCatalogDimBinding>,
): Promise<HostCatalogOverview> {
  const res = await request('/flow-api/host/config', {
    method: 'PUT',
    data: { settings },
  });
  return unwrap<HostCatalogOverview>(res) || {};
}

export async function previewHostCatalog(
  dimension: HostCatalogDimension,
  keyword?: string,
  limit = 50,
): Promise<HostCatalogItem[]> {
  const res = await request('/flow-api/host/config/catalog/preview', {
    method: 'GET',
    params: { dimension, keyword: keyword || undefined, limit },
  });
  const data = unwrap<HostCatalogItem[]>(res);
  return Array.isArray(data) ? data : [];
}

/** 可映射到主体上的字段 */
export type HostPrincipalField =
  | 'userId'
  | 'username'
  | 'userType'
  | 'deptId'
  | 'deptIds'
  | 'roles'
  | 'permissions';

export type HostPrincipalMode = 'API' | 'HEADER';

export interface HostPrincipalSettings {
  enabled?: boolean;
  mode?: HostPrincipalMode;
  cacheSeconds?: number;
  forwardHeaders?: string[];
  fields?: Partial<Record<HostPrincipalField, string>>;
  headerNames?: Partial<Record<HostPrincipalField, string>>;
  trustProxyHeaders?: boolean;
  adminUserTypes?: string[];
  privacyRules?: PrivacyAccessRule[];
  /** @deprecated 由 privacyRules 替代 */
  privacyRevealRoles?: string[];
  /** @deprecated 未生效 */
  privacyMaskRoles?: string[];
  privacyProfileId?: string;
  privacyFieldSuffix?: string;
  privacyExtraFields?: string[];
  privacyStripSuffix?: boolean;
  ingressCallerEnabled?: boolean;
  ingressRules?: CallerAccessRule[];
}

export interface HostPrincipalOverview {
  spiOverride?: boolean;
  settings?: HostPrincipalSettings;
  api?: HostCatalogApiMeta;
  fields?: HostPrincipalField[];
  defaultHeaderNames?: Record<string, string>;
}

export interface HostPrincipalTestResult {
  resolved?: boolean;
  mode?: HostPrincipalMode;
  apiPublished?: boolean;
  raw?: Record<string, any>;
  principal?: Record<string, any>;
  message?: string;
}

export async function getHostPrincipalOverview(): Promise<HostPrincipalOverview> {
  const res = await request('/flow-api/host/config/principal', {
    method: 'GET',
  });
  return unwrap<HostPrincipalOverview>(res) || {};
}

export async function saveHostPrincipalSettings(
  settings: HostPrincipalSettings,
): Promise<HostPrincipalOverview> {
  const res = await request('/flow-api/host/config/principal', {
    method: 'PUT',
    data: settings,
  });
  return unwrap<HostPrincipalOverview>(res) || {};
}

/** 用当前管理端请求跑一次解析（允许草稿），用于对字段名 */
export async function testHostPrincipal(): Promise<HostPrincipalTestResult> {
  const res = await request('/flow-api/host/config/principal/test', {
    method: 'POST',
  });
  return unwrap<HostPrincipalTestResult>(res) || {};
}

export interface HostPlatformAccessDefaults {
  authMode?: string;
  rateLimitEnabled?: boolean;
  rateLimitQps?: number;
  ipAllowlist?: string;
  timeoutMs?: number;
  ingressCallerEnabled?: boolean;
  ingressRules?: CallerAccessRule[];
  privacyRules?: PrivacyAccessRule[];
  privacyProfileId?: string;
  privacyFieldSuffix?: string;
  privacyExtraFields?: string[];
  privacyStripSuffix?: boolean;
}

export async function getPlatformAccessDefaults(): Promise<HostPlatformAccessDefaults> {
  const res = await request('/flow-api/host/config/platform-access-defaults', {
    method: 'GET',
  });
  return unwrap<HostPlatformAccessDefaults>(res) || {};
}

export async function savePlatformAccessDefaults(
  payload: HostPlatformAccessDefaults,
): Promise<HostPlatformAccessDefaults> {
  const res = await request('/flow-api/host/config/platform-access-defaults', {
    method: 'PUT',
    data: payload,
  });
  return unwrap<HostPlatformAccessDefaults>(res) || {};
}

export type PrivacyDecryptAlg = 'SM4' | 'AES' | 'PLAIN';

export interface PrivacyMaskRule {
  id?: string;
  aliases?: string[];
  /** EXACT=全等；CONTAINS=包含；REGEX 仅兼容旧 JSON */
  matchMode?: 'EXACT' | 'CONTAINS' | 'REGEX';
  method?: string;
  keepHead?: number | null;
  keepTail?: number | null;
  /** 已忽略。中间按原文剩余长度填 * */
  maskLen?: number | null;
  maskChar?: string;
}

export interface HostPrivacyProfile {
  id?: string;
  name?: string;
  decryptAlg?: string;
  decryptMode?: string;
  decryptEncoding?: string;
  decryptIvMode?: string;
  decryptIvFixed?: string;
  decryptKey?: string;
  decryptKeySet?: boolean;
  fieldSuffix?: string;
  extraFields?: string[];
  stripSuffix?: boolean | null;
  rules?: PrivacyMaskRule[];
}

export interface HostPrivacyProfilesData {
  profiles?: HostPrivacyProfile[];
}

export async function getHostPrivacyProfiles(): Promise<HostPrivacyProfilesData> {
  const res = await request('/flow-api/host/config/privacy-profiles', { method: 'GET' });
  return unwrap<HostPrivacyProfilesData>(res) || { profiles: [] };
}

export async function saveHostPrivacyProfiles(
  profiles: HostPrivacyProfile[],
): Promise<HostPrivacyProfilesData> {
  const res = await request('/flow-api/host/config/privacy-profiles', {
    method: 'PUT',
    data: { profiles },
  });
  return unwrap<HostPrivacyProfilesData>(res) || { profiles: [] };
}

export const PRIVACY_BUILTIN_PROFILE_ID = 'builtin';

export function privacyFamilyOf(alg?: string): PrivacyDecryptAlg {
  const t = (alg || '').toUpperCase().replace(/-/g, '_');
  if (t === 'PLAIN' || t === 'NONE' || t === 'IDENTITY') return 'PLAIN';
  if (t.startsWith('AES')) return 'AES';
  return 'SM4';
}

function inferMode(alg?: string): string {
  const t = (alg || '').toUpperCase().replace(/-/g, '_');
  if (t.includes('ECB')) return 'ECB';
  if (t.includes('GCM')) return 'GCM';
  return 'CBC';
}

function inferIvMode(alg?: string, mode?: string): string {
  const m = (mode || inferMode(alg)).toUpperCase();
  if (m === 'ECB') return 'NONE';
  return 'PREPEND';
}

export function hydratePrivacyCrypto(row?: Partial<HostPrivacyProfile> | null): {
  decryptAlg: PrivacyDecryptAlg;
  decryptMode: string;
  decryptEncoding: string;
  decryptIvMode: string;
  decryptIvFixed?: string;
} {
  const decryptAlg = privacyFamilyOf(row?.decryptAlg);
  const decryptMode = row?.decryptMode || inferMode(row?.decryptAlg);
  const decryptEncoding = row?.decryptEncoding || 'HEX';
  const decryptIvMode = row?.decryptIvMode || inferIvMode(row?.decryptAlg, decryptMode);
  return {
    decryptAlg,
    decryptMode,
    decryptEncoding,
    decryptIvMode,
    decryptIvFixed: row?.decryptIvFixed,
  };
}

export function privacySpecLabel(profile?: HostPrivacyProfile | null): string {
  const family = privacyFamilyOf(profile?.decryptAlg);
  if (family === 'PLAIN') return '明文';
  const mode = (profile?.decryptMode || inferMode(profile?.decryptAlg) || 'CBC').toUpperCase();
  const enc = (profile?.decryptEncoding || 'HEX').toUpperCase();
  return `${family}/${mode} · ${enc}`;
}

export function privacyAlgLabel(alg?: string): string {
  return privacySpecLabel({ decryptAlg: alg });
}

export function privacyRuleSummary(profile?: HostPrivacyProfile | null): string {
  const n = profile?.rules?.length ?? 0;
  const key = profile?.decryptKeySet ? '已配密钥' : 'YAML 回退';
  return `${privacySpecLabel(profile)} · ${n} 条脱敏 · ${key}`;
}


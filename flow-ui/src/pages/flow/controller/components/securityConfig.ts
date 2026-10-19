/**
 * securityConfig.ts
 * ControllerForm 安全/入口配置表单字段与 JSON 配置之间的双向转换。
 */

import {
  type CallerAccessRule,
  parseCallerAccessRules,
  normalizeCallerAccessRule,
} from '@/utils/principalMatch';

export type CallerMatchMode = 'ALL' | 'ANY';

/** Redis 故障时：继承全局 / 放行 / 拒绝 */
export type RateLimitFailMode = 'INHERIT' | 'OPEN' | 'CLOSED';

export interface CallerPolicyForm {
  secCallerEnabled: boolean;
  secCallerRules: CallerAccessRule[];
}

export interface SecurityFormDefaults extends CallerPolicyForm {
  secAuthMode: 'INHERIT' | 'NONE' | 'HOST' | 'OPEN';
  secAntiReplayOverride: boolean;
  secAntiReplay: boolean;
  secRateLimitOverride: boolean;
  secRateLimitEnabled: boolean;
  secRateLimitQps: number;
  /** 令牌桶容量；空表示等于 QPS */
  secRateLimitBurst: number | null;
  secRateLimitFailMode: RateLimitFailMode;
  secIpOverride: boolean;
  secIpAllowlist: string;
  secTimeoutOverride: boolean;
  secTimeoutMs: number;
}

export interface ApiCallerPolicy {
  enabled: boolean;
  rules?: CallerAccessRule[];
}

export interface SecurityConfig {
  authMode: 'INHERIT' | 'NONE' | 'HOST' | 'OPEN';
  antiReplay: boolean | null;
  rateLimitEnabled: boolean | null;
  rateLimitQps: number | null;
  rateLimitBurst: number | null;
  /** true=Redis 故障时放行，false=拒绝，null=继承 */
  rateLimitFailOpen: boolean | null;
  ipAllowlist: string | null;
  timeoutMs: number | null;
  callerPolicy?: ApiCallerPolicy | null;
}

const DEFAULTS: SecurityFormDefaults = {
  secAuthMode: 'INHERIT',
  secAntiReplayOverride: false,
  secAntiReplay: true,
  secRateLimitOverride: false,
  secRateLimitEnabled: false,
  secRateLimitQps: 100,
  secRateLimitBurst: null,
  secRateLimitFailMode: 'INHERIT',
  secIpOverride: false,
  secIpAllowlist: '',
  secTimeoutOverride: false,
  secTimeoutMs: 30000,
  secCallerEnabled: false,
  secCallerRules: [],
};

const isSet = (v: unknown) => v !== null && v !== undefined;

/** 将 securityConfig JSON 还原为表单字段 */
export function parseSecurityConfigToForm(raw?: string | object | null): SecurityFormDefaults {
  if (!raw) return { ...DEFAULTS };
  try {
    const cfg = typeof raw === 'string' ? JSON.parse(raw) : raw;
    const authMode = cfg?.authMode || 'INHERIT';
    const cp = cfg?.callerPolicy || {};
    let failMode: RateLimitFailMode = 'INHERIT';
    if (cfg?.rateLimitFailOpen === true) failMode = 'OPEN';
    else if (cfg?.rateLimitFailOpen === false) failMode = 'CLOSED';
    return {
      secAuthMode: ['INHERIT', 'NONE', 'HOST', 'OPEN'].includes(authMode) ? authMode : 'INHERIT',
      secAntiReplayOverride: isSet(cfg?.antiReplay),
      secAntiReplay: cfg?.antiReplay !== false,
      secRateLimitOverride:
        isSet(cfg?.rateLimitEnabled)
        || isSet(cfg?.rateLimitQps)
        || isSet(cfg?.rateLimitBurst)
        || isSet(cfg?.rateLimitFailOpen),
      secRateLimitEnabled: !!cfg?.rateLimitEnabled,
      secRateLimitQps: typeof cfg?.rateLimitQps === 'number' ? cfg.rateLimitQps : 100,
      secRateLimitBurst: typeof cfg?.rateLimitBurst === 'number' ? cfg.rateLimitBurst : null,
      secRateLimitFailMode: failMode,
      secIpOverride: isSet(cfg?.ipAllowlist),
      secIpAllowlist: typeof cfg?.ipAllowlist === 'string' ? cfg.ipAllowlist : '',
      secTimeoutOverride: isSet(cfg?.timeoutMs),
      secTimeoutMs: typeof cfg?.timeoutMs === 'number' ? cfg.timeoutMs : 30000,
      secCallerEnabled: !!cp?.enabled,
      secCallerRules: parseCallerAccessRules(cp),
    };
  } catch {
    return { ...DEFAULTS };
  }
}

/** 由表单字段组装 securityConfig 对象 */
export function buildSecurityConfigFromForm(formValues: Record<string, any>): SecurityConfig {
  const authMode = formValues.secAuthMode || 'INHERIT';
  const callerEnabled = !!formValues.secCallerEnabled;
  const rules = Array.isArray(formValues.secCallerRules)
    ? formValues.secCallerRules.map((r: CallerAccessRule) => normalizeCallerAccessRule(r))
    : [];
  const callerPolicy: ApiCallerPolicy = {
    enabled: callerEnabled,
    rules: callerEnabled ? rules : [],
  };
  const rateOverride = !!formValues.secRateLimitOverride;
  const failMode: RateLimitFailMode = formValues.secRateLimitFailMode || 'INHERIT';

  return {
    authMode,
    antiReplay: formValues.secAntiReplayOverride ? !!formValues.secAntiReplay : null,
    rateLimitEnabled: rateOverride ? !!formValues.secRateLimitEnabled : null,
    rateLimitQps: rateOverride ? (formValues.secRateLimitQps ?? 100) : null,
    rateLimitBurst:
      rateOverride && typeof formValues.secRateLimitBurst === 'number' ? formValues.secRateLimitBurst : null,
    rateLimitFailOpen: rateOverride && failMode !== 'INHERIT' ? failMode === 'OPEN' : null,
    ipAllowlist: formValues.secIpOverride
      ? (formValues.secIpAllowlist ?? '')
      : null,
    timeoutMs: formValues.secTimeoutOverride
      ? (typeof formValues.secTimeoutMs === 'number' ? formValues.secTimeoutMs : 30000)
      : null,
    callerPolicy,
  };
}

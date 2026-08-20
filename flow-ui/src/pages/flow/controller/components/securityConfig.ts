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
  secIpOverride: false,
  secIpAllowlist: '',
  secTimeoutOverride: false,
  secTimeoutMs: 30000,
  secCallerEnabled: false,
  secCallerRules: [],
};

/** 将 securityConfig JSON 还原为表单字段 */
export function parseSecurityConfigToForm(raw?: string | object | null): SecurityFormDefaults {
  if (!raw) return { ...DEFAULTS };
  try {
    const cfg = typeof raw === 'string' ? JSON.parse(raw) : raw;
    const authMode = cfg?.authMode || 'INHERIT';
    const cp = cfg?.callerPolicy || {};
    return {
      secAuthMode: ['INHERIT', 'NONE', 'HOST', 'OPEN'].includes(authMode) ? authMode : 'INHERIT',
      secAntiReplayOverride: cfg?.antiReplay !== null && cfg?.antiReplay !== undefined,
      secAntiReplay: cfg?.antiReplay !== false,
      secRateLimitOverride:
        (cfg?.rateLimitEnabled !== null && cfg?.rateLimitEnabled !== undefined)
        || (cfg?.rateLimitQps !== null && cfg?.rateLimitQps !== undefined),
      secRateLimitEnabled: !!cfg?.rateLimitEnabled,
      secRateLimitQps: typeof cfg?.rateLimitQps === 'number' ? cfg.rateLimitQps : 100,
      secIpOverride: cfg?.ipAllowlist !== null && cfg?.ipAllowlist !== undefined,
      secIpAllowlist: typeof cfg?.ipAllowlist === 'string' ? cfg.ipAllowlist : '',
      secTimeoutOverride: cfg?.timeoutMs !== null && cfg?.timeoutMs !== undefined,
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

  return {
    authMode,
    antiReplay: formValues.secAntiReplayOverride ? !!formValues.secAntiReplay : null,
    rateLimitEnabled: formValues.secRateLimitOverride ? !!formValues.secRateLimitEnabled : null,
    rateLimitQps: formValues.secRateLimitOverride
      ? (formValues.secRateLimitQps ?? 100)
      : null,
    ipAllowlist: formValues.secIpOverride
      ? (formValues.secIpAllowlist ?? '')
      : null,
    timeoutMs: formValues.secTimeoutOverride
      ? (typeof formValues.secTimeoutMs === 'number' ? formValues.secTimeoutMs : 30000)
      : null,
    callerPolicy,
  };
}

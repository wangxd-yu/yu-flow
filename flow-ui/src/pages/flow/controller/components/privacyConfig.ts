/**
 * privacyConfig.ts
 * 接口 / 目录出站隐私策略表单 ↔ JSON。
 */

export type PrivacyMode = 'INHERIT' | 'ON' | 'OFF';

export interface ApiPrivacyConfig {
  enabled?: boolean | null;
  inherit?: boolean | null;
  profileId?: string | null;
  fieldSuffix?: string | null;
  extraFields?: string[];
  stripSuffix?: boolean | null;
}

export interface PrivacyFormDefaults {
  privacyMode: PrivacyMode;
  privacyInherit: boolean;
  privacyProfileId: string;
  privacyFieldSuffix: string;
  privacyExtraFields: string[];
  privacyStripSuffixOverride: boolean;
  privacyStripSuffix: boolean;
}

const DEFAULTS: PrivacyFormDefaults = {
  privacyMode: 'INHERIT',
  privacyInherit: true,
  privacyProfileId: '',
  privacyFieldSuffix: '',
  privacyExtraFields: [],
  privacyStripSuffixOverride: false,
  privacyStripSuffix: true,
};

function asStringList(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v
    .map((x) => (x == null ? '' : String(x).trim()))
    .filter(Boolean);
}

export function parsePrivacyConfigToForm(raw?: string | object | null): PrivacyFormDefaults {
  if (!raw) return { ...DEFAULTS };
  try {
    const cfg = (typeof raw === 'string' ? JSON.parse(raw) : raw) as ApiPrivacyConfig;
    let privacyMode: PrivacyMode = 'INHERIT';
    if (cfg?.enabled === true) privacyMode = 'ON';
    else if (cfg?.enabled === false) privacyMode = 'OFF';
    return {
      privacyMode,
      privacyInherit: cfg?.inherit !== false,
      privacyProfileId: typeof cfg?.profileId === 'string' ? cfg.profileId : '',
      privacyFieldSuffix: typeof cfg?.fieldSuffix === 'string' ? cfg.fieldSuffix : '',
      privacyExtraFields: asStringList(cfg?.extraFields),
      privacyStripSuffixOverride: cfg?.stripSuffix !== null && cfg?.stripSuffix !== undefined,
      privacyStripSuffix: cfg?.stripSuffix !== false,
    };
  } catch {
    return { ...DEFAULTS };
  }
}

/** 空对象表示全部继承（目录可写成空串） */
export function buildPrivacyConfigFromForm(formValues: Record<string, any>): ApiPrivacyConfig {
  const mode = (formValues.privacyMode || 'INHERIT') as PrivacyMode;
  const cfg: ApiPrivacyConfig = {};
  if (mode === 'ON') cfg.enabled = true;
  if (mode === 'OFF') cfg.enabled = false;
  cfg.inherit = formValues.privacyInherit !== false;
  const profileId = typeof formValues.privacyProfileId === 'string'
    ? formValues.privacyProfileId.trim()
    : '';
  if (profileId) cfg.profileId = profileId;
  const suffix = typeof formValues.privacyFieldSuffix === 'string'
    ? formValues.privacyFieldSuffix.trim()
    : '';
  if (suffix) cfg.fieldSuffix = suffix;
  const extra = asStringList(formValues.privacyExtraFields);
  if (extra.length) cfg.extraFields = extra;
  if (formValues.privacyStripSuffixOverride) {
    cfg.stripSuffix = !!formValues.privacyStripSuffix;
  }
  return cfg;
}

export function stringifyPrivacyConfig(cfg: ApiPrivacyConfig | null | undefined): string {
  if (!cfg) return '';
  const keys = Object.keys(cfg).filter((k) => (cfg as any)[k] !== undefined);
  if (keys.length === 0) return '';
  if (keys.length === 1 && keys[0] === 'inherit' && cfg.inherit !== false) return '';
  return JSON.stringify(cfg);
}

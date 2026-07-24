/** 与后端 PasswordPolicy 对齐的密码复杂度规则 */

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 64;

export type PasswordRuleKey =
  | 'length'
  | 'upper'
  | 'lower'
  | 'digit'
  | 'special';

export interface PasswordRule {
  key: PasswordRuleKey;
  label: string;
  test: (pwd: string) => boolean;
}

export const PASSWORD_RULES: PasswordRule[] = [
  {
    key: 'length',
    label: `至少 ${PASSWORD_MIN} 位`,
    test: (p) => p.length >= PASSWORD_MIN && p.length <= PASSWORD_MAX,
  },
  {
    key: 'upper',
    label: '包含大写字母',
    test: (p) => /[A-Z]/.test(p),
  },
  {
    key: 'lower',
    label: '包含小写字母',
    test: (p) => /[a-z]/.test(p),
  },
  {
    key: 'digit',
    label: '包含数字',
    test: (p) => /[0-9]/.test(p),
  },
  {
    key: 'special',
    label: '包含特殊字符 (!@#$…)',
    test: (p) => /[!@#$%^&*()_+\-=[\]{}|;:'",.<>/?`~\\]/.test(p),
  },
];

export function evaluatePassword(password: string) {
  const checks = PASSWORD_RULES.map((r) => ({
    ...r,
    ok: r.test(password || ''),
  }));
  const passed = checks.filter((c) => c.ok).length;
  const valid = passed === PASSWORD_RULES.length && !!password;
  /** 0–4 强度档：无效 / 弱 / 中 / 强 / 很强 */
  let score = 0;
  if (password) {
    score = Math.min(4, passed);
  }
  const levelLabel =
    score <= 1 ? '弱' : score === 2 ? '较弱' : score === 3 ? '中' : score === 4 ? '强' : '很强';
  return { checks, passed, valid, score, levelLabel };
}

/** Ant Design Form validator */
export function passwordComplexityValidator(_: any, value: string) {
  if (!value) {
    return Promise.reject(new Error('请输入密码'));
  }
  const { checks, valid } = evaluatePassword(value);
  if (valid) return Promise.resolve();
  const msg = checks
    .filter((c) => !c.ok)
    .map((c) => c.label)
    .join('、');
  return Promise.reject(new Error(`密码不符合要求：${msg}`));
}

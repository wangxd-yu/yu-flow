/**
 * Spring 6 字段 Cron（秒 分 时 日 月 周）解析与下次执行时间推算。
 *
 * 后端用 Spring {@code CronExpression} 校验，前端只做「不比后端更严」的判断：
 * - invalid：字段数不对或字段值越界，后端同样会拒绝，可用于表单拦截；
 * - unsupported：L / W / # 等高级语法或日+周同时限制，语义有歧义，仅放弃预览，不拦截提交。
 */

export type CronAnalysisStatus = 'ok' | 'unsupported' | 'invalid';

export interface CronAnalysis {
  status: CronAnalysisStatus;
  /** invalid 为不合法原因，unsupported 为无法预览的原因 */
  message?: string;
  /** status=ok 时的最近若干次执行时间 */
  nextRuns: Date[];
}

/** Spring 支持的宏，展开为等价 6 字段表达式 */
const MACROS: Record<string, string> = {
  '@yearly': '0 0 0 1 1 *',
  '@annually': '0 0 0 1 1 *',
  '@monthly': '0 0 0 1 * *',
  '@weekly': '0 0 0 * * 0',
  '@daily': '0 0 0 * * *',
  '@midnight': '0 0 0 * * *',
  '@hourly': '0 0 * * * *',
};

const MONTH_NAMES = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
const DAY_NAMES = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

/** 超出预览能力的语法：交给后端校验 */
const ADVANCED_SYNTAX = /[LW#]/i;

/** 搜索上限：4 年内无匹配即认为表达式实际上不会触发 */
const SEARCH_YEARS = 4;

class CronFieldError extends Error {}

interface ParsedCron {
  seconds: Set<number>;
  minutes: Set<number>;
  hours: Set<number>;
  daysOfMonth: Set<number>;
  months: Set<number>;
  daysOfWeek: Set<number>;
  domRestricted: boolean;
  dowRestricted: boolean;
}

function parseSingle(token: string, min: number, max: number, names?: string[]): number {
  const upper = token.toUpperCase();
  if (names) {
    const idx = names.indexOf(upper);
    if (idx >= 0) return idx + min;
  }
  if (!/^\d+$/.test(token)) {
    throw new CronFieldError(`无法识别「${token}」`);
  }
  const value = Number(token);
  if (value < min || value > max) {
    throw new CronFieldError(`「${token}」超出范围 ${min}-${max}`);
  }
  return value;
}

/** 解析单个字段为可命中值集合；`?` 与 `*` 等价（是否受限由调用方判断） */
function parseField(field: string, min: number, max: number, names?: string[]): Set<number> {
  const all = () => {
    const set = new Set<number>();
    for (let i = min; i <= max; i += 1) set.add(i);
    return set;
  };
  if (field === '*' || field === '?') return all();

  const result = new Set<number>();
  for (const part of field.split(',')) {
    if (!part) throw new CronFieldError('存在空的取值项');
    const [rangePart, stepPart, ...rest] = part.split('/');
    if (rest.length) throw new CronFieldError(`「${part}」步长语法有误`);

    let step = 1;
    if (stepPart !== undefined) {
      if (!/^\d+$/.test(stepPart) || Number(stepPart) < 1) {
        throw new CronFieldError(`「${part}」步长必须为正整数`);
      }
      step = Number(stepPart);
    }

    let from: number;
    let to: number;
    if (rangePart === '*' || rangePart === '?') {
      from = min;
      to = max;
    } else if (rangePart.includes('-')) {
      const [a, b] = rangePart.split('-');
      from = parseSingle(a, min, max, names);
      to = parseSingle(b, min, max, names);
      if (from > to) throw new CronFieldError(`「${rangePart}」区间起点大于终点`);
    } else {
      from = parseSingle(rangePart, min, max, names);
      // 「a/n」表示从 a 起按步长递增到字段上限
      to = stepPart === undefined ? from : max;
    }
    for (let v = from; v <= to; v += step) result.add(v);
  }
  if (!result.size) throw new CronFieldError('未匹配到任何取值');
  return result;
}

function parseDaysOfWeek(field: string): Set<number> {
  // Spring：0-7，0 与 7 均为周日；统一折叠到 JS 的 0-6
  const raw = parseField(field, 0, 7, DAY_NAMES);
  const normalized = new Set<number>();
  raw.forEach((v) => normalized.add(v === 7 ? 0 : v));
  return normalized;
}

function isWildcard(field: string): boolean {
  return field === '*' || field === '?';
}

function nextRunAfter(cron: ParsedCron, from: Date): Date | null {
  let cursor = new Date(from.getTime() + 1000);
  cursor.setMilliseconds(0);
  const limit = new Date(from.getFullYear() + SEARCH_YEARS, 0, 1).getTime();

  while (cursor.getTime() < limit) {
    if (!cron.months.has(cursor.getMonth() + 1)) {
      cursor = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1, 0, 0, 0, 0);
      continue;
    }
    if (!matchesDay(cron, cursor)) {
      cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1, 0, 0, 0, 0);
      continue;
    }
    if (!cron.hours.has(cursor.getHours())) {
      cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate(), cursor.getHours() + 1, 0, 0, 0);
      continue;
    }
    if (!cron.minutes.has(cursor.getMinutes())) {
      cursor = new Date(
        cursor.getFullYear(), cursor.getMonth(), cursor.getDate(),
        cursor.getHours(), cursor.getMinutes() + 1, 0, 0,
      );
      continue;
    }
    if (!cron.seconds.has(cursor.getSeconds())) {
      cursor = new Date(cursor.getTime() + 1000);
      continue;
    }
    return cursor;
  }
  return null;
}

function matchesDay(cron: ParsedCron, date: Date): boolean {
  if (cron.domRestricted) return cron.daysOfMonth.has(date.getDate());
  if (cron.dowRestricted) return cron.daysOfWeek.has(date.getDay());
  return true;
}

/**
 * 解析表达式并推算最近几次执行时间。
 *
 * @param raw 原始表达式，支持 @daily 等宏
 * @param options count 预览条数（默认 3），from 起算时间（默认当前）
 */
export function analyzeCron(
  raw?: string | null,
  options?: { count?: number; from?: Date },
): CronAnalysis {
  const expr = (raw || '').trim();
  if (!expr) {
    return { status: 'invalid', message: '请输入 Cron 表达式', nextRuns: [] };
  }

  const expanded = MACROS[expr.toLowerCase()] || expr;
  if (expanded === expr && expr.startsWith('@')) {
    return { status: 'invalid', message: `不支持的宏「${expr}」`, nextRuns: [] };
  }

  const fields = expanded.split(/\s+/);
  if (fields.length !== 6) {
    return {
      status: 'invalid',
      message: `需要 6 个字段（秒 分 时 日 月 周），当前 ${fields.length} 个`,
      nextRuns: [],
    };
  }
  if (ADVANCED_SYNTAX.test(expanded)) {
    return {
      status: 'unsupported',
      message: '含 L / W / # 高级语法，暂不支持预览，提交后由后端校验',
      nextRuns: [],
    };
  }

  const [sec, min, hour, dom, month, dow] = fields;
  const specs = [
    { label: '秒', field: sec, min: 0, max: 59 },
    { label: '分', field: min, min: 0, max: 59 },
    { label: '时', field: hour, min: 0, max: 23 },
    { label: '日', field: dom, min: 1, max: 31 },
    { label: '月', field: month, min: 1, max: 12, names: MONTH_NAMES },
    { label: '周', field: dow, min: 0, max: 7, names: DAY_NAMES },
  ];

  const parsed: Set<number>[] = [];
  for (let i = 0; i < specs.length; i += 1) {
    const spec = specs[i];
    try {
      parsed.push(i === 5 ? parseDaysOfWeek(spec.field) : parseField(spec.field, spec.min, spec.max, spec.names));
    } catch (e) {
      return {
        status: 'invalid',
        message: `「${spec.label}」字段${e instanceof Error ? e.message : '格式有误'}`,
        nextRuns: [],
      };
    }
  }

  const cron: ParsedCron = {
    seconds: parsed[0],
    minutes: parsed[1],
    hours: parsed[2],
    daysOfMonth: parsed[3],
    months: parsed[4],
    daysOfWeek: parsed[5],
    domRestricted: !isWildcard(dom),
    dowRestricted: !isWildcard(dow),
  };

  if (cron.domRestricted && cron.dowRestricted) {
    return {
      status: 'unsupported',
      message: '同时限制「日」与「周」，各调度实现语义不一致，已跳过预览',
      nextRuns: [],
    };
  }

  const count = Math.max(1, options?.count ?? 3);
  const nextRuns: Date[] = [];
  let cursor = options?.from ?? new Date();
  for (let i = 0; i < count; i += 1) {
    const next = nextRunAfter(cron, cursor);
    if (!next) break;
    nextRuns.push(next);
    cursor = next;
  }
  if (!nextRuns.length) {
    return {
      status: 'unsupported',
      message: `未来 ${SEARCH_YEARS} 年内没有匹配时间，请检查日期组合`,
      nextRuns: [],
    };
  }
  return { status: 'ok', nextRuns };
}

const pad = (n: number) => String(n).padStart(2, '0');

/** 预览用时间文案：YYYY-MM-DD HH:mm:ss */
export function formatCronTime(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

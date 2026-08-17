import { analyzeCron, formatCronTime } from '../cron';

const FROM = new Date(2026, 7, 13, 10, 30, 15); // 2026-08-13 10:30:15 周四

const runs = (expr: string, count = 3) =>
  analyzeCron(expr, { from: FROM, count }).nextRuns.map(formatCronTime);

describe('analyzeCron', () => {
  it('每 5 分钟：从下一个整 5 分开始', () => {
    expect(runs('0 0/5 * * * ?')).toEqual([
      '2026-08-13 10:35:00',
      '2026-08-13 10:40:00',
      '2026-08-13 10:45:00',
    ]);
  });

  it('每天固定时刻：跨天推进', () => {
    expect(runs('0 0 2 * * ?')).toEqual([
      '2026-08-14 02:00:00',
      '2026-08-15 02:00:00',
      '2026-08-16 02:00:00',
    ]);
  });

  it('按星期：支持名称与数字，周日 0/7 等价', () => {
    expect(runs('0 0 9 ? * MON', 2)).toEqual(['2026-08-17 09:00:00', '2026-08-24 09:00:00']);
    expect(runs('0 0 9 ? * 0', 1)).toEqual(runs('0 0 9 ? * 7', 1));
  });

  it('列表 / 区间 / 月份名称', () => {
    expect(runs('0 15,45 * * * ?', 2)).toEqual(['2026-08-13 10:45:00', '2026-08-13 11:15:00']);
    expect(runs('30 0 0 1 JAN-FEB ?', 2)).toEqual(['2027-01-01 00:00:30', '2027-02-01 00:00:30']);
  });

  it('宏展开', () => {
    expect(runs('@daily', 1)).toEqual(['2026-08-14 00:00:00']);
    expect(analyzeCron('@bogus', { from: FROM }).status).toBe('invalid');
  });

  it('闰日等稀疏组合仍可推算', () => {
    expect(runs('0 0 0 29 2 ?', 1)).toEqual(['2028-02-29 00:00:00']);
  });

  it('字段数不符 / 值越界视为不合法', () => {
    expect(analyzeCron('* * * * *', { from: FROM })).toMatchObject({ status: 'invalid' });
    expect(analyzeCron('0 99 * * * ?', { from: FROM }).message).toContain('分');
    expect(analyzeCron('0 0 0 * ABC ?', { from: FROM }).message).toContain('月');
    expect(analyzeCron('0 5-1 * * * ?', { from: FROM }).message).toContain('区间');
    expect(analyzeCron('', { from: FROM })).toMatchObject({ status: 'invalid' });
  });

  it('高级语法与日+周同时限制只跳过预览，不判为不合法', () => {
    expect(analyzeCron('0 0 0 L * ?', { from: FROM }).status).toBe('unsupported');
    expect(analyzeCron('0 0 0 ? * FRI#3', { from: FROM }).status).toBe('unsupported');
    expect(analyzeCron('0 0 0 15 * MON', { from: FROM }).status).toBe('unsupported');
  });

  it('未来无匹配时间时给出提示而非静默空列表', () => {
    const result = analyzeCron('0 0 0 30 2 ?', { from: FROM });
    expect(result.status).toBe('unsupported');
    expect(result.message).toContain('没有匹配时间');
  });
});

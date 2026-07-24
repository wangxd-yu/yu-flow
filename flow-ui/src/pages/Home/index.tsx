import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Col, Empty, Row, Spin, Tag, Typography } from 'antd';
import { history, useModel } from '@umijs/max';
import {
  ApiOutlined,
  ClockCircleOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  LayoutOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  ScheduleOutlined,
  ClusterOutlined,
  ThunderboltOutlined,
  SettingOutlined,
} from '@ant-design/icons';

import { queryAutoApiConfigList } from '@/services/flow/flowController';
import { queryDataSourcePage } from '@/services/flow/dataSource';
import { queryTaskPage } from '@/services/flow/taskService';
import { queryServiceFlowPage } from '@/services/flow/serviceFlowService';
import { pageOpenPlatforms } from '@/services/flow/openPlatformService';
import { queryPageList } from '@/pages/PageManage/services/pageManage';
import {
  getMetricsAnomalies,
  type AssetMetricsRankItem,
} from '@/services/flow/assetMetrics';
import { renderHealthTag } from '@/components/flow/AssetHealthTag';
import { Pie } from '@ant-design/plots';

import styles from './index.less';

const { Text, Title } = Typography;

type Stats = {
  apis: number;
  tasks: number;
  services: number;
  platforms: number;
  sources: number;
  pages: number;
};

const EMPTY_STATS: Stats = {
  apis: 0,
  tasks: 0,
  services: 0,
  platforms: 0,
  sources: 0,
  pages: 0,
};

function totalOf(res: any): number {
  if (res == null) return 0;
  if (typeof res.total === 'number') return res.total;
  if (typeof res?.data?.total === 'number') return res.data.total;
  return 0;
}

function openAsset(item: AssetMetricsRankItem) {
  const tab = 'runtime';
  if (item.assetType === 'API') {
    history.push(`/flow/api?apiId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  } else if (item.assetType === 'TASK') {
    history.push(`/flow/task?taskId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  } else if (item.assetType === 'PLATFORM') {
    history.push(`/flow/open-platform?platformId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  } else {
    history.push(`/flow/service?serviceId=${encodeURIComponent(item.assetId)}&tab=${tab}`);
  }
}

function assetTypeLabel(t: string) {
  if (t === 'API') return '接口';
  if (t === 'TASK') return '任务';
  if (t === 'SERVICE') return '服务';
  if (t === 'PLATFORM') return '开放平台';
  return t;
}

function getTimeGreeting() {
  const hour = new Date().getHours();
  if (hour < 6) return '凌晨好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}

const HomePage: React.FC = () => {
  const { initialState } = useModel('@@initialState');
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<Stats>(EMPTY_STATS);
  const [anomalies, setAnomalies] = useState<AssetMetricsRankItem[]>([]);
  const [anomalyLoading, setAnomalyLoading] = useState(true);

  useEffect(() => {
    if (!initialState?.isLogin) return;

    const fetchStats = async () => {
      setLoading(true);
      try {
        const [apiRes, taskRes, serviceRes, platformRes, sourceRes, pageRes] =
          await Promise.all([
            queryAutoApiConfigList({ page: 0, size: 1 }).catch(() => null),
            queryTaskPage({ page: 0, size: 1 }).catch(() => null),
            queryServiceFlowPage({ page: 0, size: 1 }).catch(() => null),
            pageOpenPlatforms({ page: 0, size: 1 }).catch(() => null),
            queryDataSourcePage({ page: 0, size: 1 }).catch(() => null),
            queryPageList({ page: 0, size: 1 }).catch(() => null),
          ]);

        setStats({
          apis: totalOf(apiRes),
          tasks: totalOf(taskRes),
          services: totalOf(serviceRes),
          platforms: totalOf(platformRes),
          sources: totalOf(sourceRes),
          pages: totalOf(pageRes),
        });
      } catch (e) {
        console.error('Failed to fetch home stats', e);
      } finally {
        setLoading(false);
      }
    };

    const fetchAnomalies = async () => {
      setAnomalyLoading(true);
      try {
        const list = await getMetricsAnomalies({ window: '24h', limit: 5 });
        setAnomalies(list || []);
      } catch {
        setAnomalies([]);
      } finally {
        setAnomalyLoading(false);
      }
    };

    fetchStats();
    fetchAnomalies();
  }, [initialState?.isLogin]);

  const metricItems: Array<{
    key: keyof Stats;
    label: string;
    hint: string;
    icon: React.ReactNode;
    path: string;
    tone: string;
  }> = [
    {
      key: 'apis',
      label: '动态接口',
      hint: '编排 / 发布 / 入站防护',
      icon: <ApiOutlined />,
      path: '/flow/api',
      tone: 'teal',
    },
    {
      key: 'tasks',
      label: '定时任务',
      hint: 'Cron 编排执行',
      icon: <ScheduleOutlined />,
      path: '/flow/task',
      tone: 'slate',
    },
    {
      key: 'services',
      label: '服务编排',
      hint: '可复用流程资产',
      icon: <ClusterOutlined />,
      path: '/flow/service',
      tone: 'cyan',
    },
    {
      key: 'platforms',
      label: '开放平台',
      hint: 'AppKey / HMAC 对外',
      icon: <SafetyCertificateOutlined />,
      path: '/flow/open-platform',
      tone: 'amber',
    },
    {
      key: 'sources',
      label: '数据源',
      hint: '外部库连接',
      icon: <CloudServerOutlined />,
      path: '/flow/dataSource',
      tone: 'blue',
    },
    {
      key: 'pages',
      label: '可视页面',
      hint: 'Amis 低代码',
      icon: <LayoutOutlined />,
      path: '/page-manage/list',
      tone: 'olive',
    },
  ];

  const composition = [
    { key: 'apis', label: '动态接口', value: stats.apis, color: '#2563EB' },
    { key: 'tasks', label: '定时任务', value: stats.tasks, color: '#0284C7' },
    { key: 'services', label: '服务编排', value: stats.services, color: '#059669' },
    { key: 'platforms', label: '开放平台', value: stats.platforms, color: '#1E40AF' },
    { key: 'sources', label: '数据源', value: stats.sources, color: '#0891B2' },
    { key: 'pages', label: '可视页面', value: stats.pages, color: '#10B981' },
  ];
  const totalAssets = composition.reduce((s, c) => s + c.value, 0);
  const pieSlices = composition.filter((c) => c.value > 0);
  const pieConfig = {
    data: pieSlices.map((c) => ({ type: c.label, value: c.value })),
    angleField: 'value',
    colorField: 'type',
    innerRadius: 0.66,
    height: 180,
    autoFit: true,
    legend: false as const,
    label: false as const,
    scale: { color: { range: pieSlices.map((c) => c.color) } },
    tooltip: { items: [{ channel: 'y' as const, valueFormatter: (v: number) => `${v} 个` }] },
  };

  const asideEntries = [
    { label: '系统配置', desc: '参数与开关', path: '/sys-config/manage', icon: <SettingOutlined /> },
    { label: '数据模型', desc: '模型与结构', path: '/data-model/list', icon: <DatabaseOutlined /> },
    { label: '接口日志', desc: '执行记录追踪', path: '/log/execution', icon: <ClockCircleOutlined /> },
  ];

  return (
    <PageContainer
      ghost
      className={styles.home}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      {/* Hero */}
      <section className={styles.hero}>
        <div className={styles.heroGlow} aria-hidden />
        <div className={styles.heroInner}>
          <div className={styles.heroMain}>
            <div className={styles.brandMark}>Yu Flow</div>
            <Title level={2} className={styles.heroTitle}>
              {getTimeGreeting()}，从编排到发布，一站掌控
            </Title>
            <Text className={styles.heroSub}>
              动态 API · 任务 / 服务编排 · 开放平台鉴权 · 入站防护与运行观测
            </Text>
            <div
              className={styles.heroStatus}
              data-tone={anomalyLoading ? 'loading' : anomalies.length > 0 ? 'warn' : 'ok'}
            >
              <span className={styles.heroStatusDot} />
              {anomalyLoading
                ? '正在检测运行状态…'
                : anomalies.length > 0
                ? `近 24h 有 ${anomalies.length} 个资产异常，建议及时排查`
                : '近 24h 运行状态良好，无异常资产'}
            </div>
            <div className={styles.heroCtas}>
              <Button
                type="primary"
                icon={<ApiOutlined />}
                onClick={() => history.push('/flow/api')}
              >
                接口管理
              </Button>
              <Button icon={<ThunderboltOutlined />} onClick={() => history.push('/flow/runtime')}>
                运行中心
              </Button>
              <Button
                icon={<SafetyCertificateOutlined />}
                onClick={() => history.push('/flow/open-platform')}
              >
                开放平台
              </Button>
            </div>
          </div>

          <div className={styles.heroAside}>
            <div className={styles.heroAsideTitle}>快捷入口</div>
            <div className={styles.heroAsideList}>
              {asideEntries.map((s) => (
                <button
                  key={s.path}
                  type="button"
                  className={styles.asideLink}
                  onClick={() => history.push(s.path)}
                >
                  <span className={styles.asideIcon}>{s.icon}</span>
                  <span className={styles.asideText}>
                    <span className={styles.asideLabel}>{s.label}</span>
                    <span className={styles.asideDesc}>{s.desc}</span>
                  </span>
                  <RightOutlined className={styles.asideArrow} />
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Asset metrics */}
      <Spin spinning={loading} wrapperClassName={styles.metricSpin}>
        <div className={styles.metricGrid}>
          {metricItems.map((m) => (
            <button
              key={m.key}
              type="button"
              className={`${styles.metricTile} ${styles[`tone_${m.tone}`]}`}
              onClick={() => history.push(m.path)}
            >
              <div className={styles.metricTop}>
                <span className={styles.metricLabel}>{m.label}</span>
                <span className={styles.metricIcon}>{m.icon}</span>
              </div>
              <div className={styles.metricValue}>{stats[m.key]}</div>
              <div className={styles.metricHint}>{m.hint}</div>
            </button>
          ))}
        </div>
      </Spin>

      <Row gutter={[14, 14]} className={styles.lower}>
        {/* Runtime anomalies */}
        <Col xs={24} lg={14}>
          <section className={styles.panel}>
            <div className={styles.panelHead}>
              <div>
                <div className={styles.panelTitle}>近 24h 运行关注</div>
                <Text type="secondary" className={styles.panelDesc}>
                  来自运行中心异常排行，点击可进入资产运行 Tab
                </Text>
              </div>
              <Button type="link" onClick={() => history.push('/flow/runtime')}>
                查看全部 <RightOutlined />
              </Button>
            </div>
            <Spin spinning={anomalyLoading}>
              {anomalies.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无异常资产，运行状态良好"
                />
              ) : (
                <ul className={styles.anomalyList}>
                  {anomalies.map((item) => (
                    <li key={`${item.assetType}-${item.assetId}`}>
                      <button
                        type="button"
                        className={styles.anomalyRow}
                        onClick={() => openAsset(item)}
                      >
                        <Tag className={styles.anomalyType}>{assetTypeLabel(item.assetType)}</Tag>
                        {renderHealthTag({
                          assetType: item.assetType,
                          assetId: item.assetId,
                          health: item.health,
                          successRate: item.successRate,
                          consecutiveFail: item.consecutiveFail,
                          totalCalls: item.totalCalls,
                          window: '24h',
                        })}
                        <span className={styles.anomalyName} title={item.assetName || item.assetId}>
                          {item.assetName || item.assetId}
                        </span>
                        <span className={styles.anomalyStat}>
                          错误率{' '}
                          <b>
                            {item.errorRate != null
                              ? `${(item.errorRate * 100).toFixed(1)}%`
                              : '—'}
                          </b>
                        </span>
                        <span className={styles.anomalyStat}>
                          调用 <b>{item.totalCalls ?? 0}</b>
                        </span>
                        <RightOutlined className={styles.anomalyArrow} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Spin>
          </section>
        </Col>

        {/* Asset composition */}
        <Col xs={24} lg={10}>
          <section className={styles.panel}>
            <div className={styles.panelHead}>
              <div>
                <div className={styles.panelTitle}>平台资产构成</div>
                <Text type="secondary" className={styles.panelDesc}>
                  各类资产数量占比
                </Text>
              </div>
            </div>
            <Spin spinning={loading}>
              {totalAssets === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无资产数据" />
              ) : (
                <div className={styles.composition}>
                  <div className={styles.donutWrap}>
                    <Pie {...pieConfig} />
                    <div className={styles.donutCenter}>
                      <span className={styles.donutTotal}>{totalAssets}</span>
                      <span className={styles.donutLabel}>资产总数</span>
                    </div>
                  </div>
                  <ul className={styles.compositionLegend}>
                    {composition.map((c) => {
                      const pct = totalAssets > 0 ? (c.value / totalAssets) * 100 : 0;
                      return (
                        <li key={c.key}>
                          <span className={styles.legendDot} style={{ background: c.color }} />
                          <span className={styles.legendLabel}>{c.label}</span>
                          <span className={styles.legendValue}>{c.value}</span>
                          <span className={styles.legendPct}>{pct.toFixed(0)}%</span>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              )}
            </Spin>
          </section>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default HomePage;

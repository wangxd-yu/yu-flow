import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Row, Col, Progress, Timeline, Tag, Spin } from 'antd';
import { useModel, history } from '@umijs/max';
import {
  ApiOutlined,
  DatabaseOutlined,
  LayoutOutlined,
  CloudServerOutlined,
  ArrowUpOutlined,
  ThunderboltOutlined,
  SafetyCertificateOutlined,
  FieldTimeOutlined,
  PlusOutlined,
  CodeOutlined
} from '@ant-design/icons';

import { queryAutoApiConfigList } from '@/pages/flow/controller/services/flowController';
import { queryDataSourcePage } from '@/pages/flow/dataSource/services/dataSource';
import { queryModelList } from '@/pages/DataModel/services/dataModel';
import { queryPageList } from '@/pages/PageManage/services/pageManage';

import styles from './index.less';

const HomePage: React.FC = () => {
  const { initialState } = useModel('@@initialState');
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    apis: 0,
    sources: 0,
    models: 0,
    pages: 0,
  });

  // Fetch real data counts from the backend
  useEffect(() => {
    const fetchStats = async () => {
      setLoading(true);
      try {
        const [apiRes, sourceRes, modelRes, pageRes] = await Promise.all([
          queryAutoApiConfigList({ page: 0, size: 1 }).catch(() => ({ total: 0 })),
          queryDataSourcePage({ page: 0, size: 1 }).catch(() => ({ total: 0 })),
          queryModelList({ page: 0, size: 1 }).catch(() => ({ total: 0 })),
          queryPageList({ page: 0, size: 1 }).catch(() => ({ total: 0 })),
        ]);

        setStats({
          apis: apiRes?.total || 0,
          sources: sourceRes?.total || 0,
          models: modelRes?.total || 0,
          pages: pageRes?.total || 0,
        });
      } catch (error) {
        console.error('Failed to fetch dashboard stats', error);
      } finally {
        setLoading(false);
      }
    };

    if (initialState?.isLogin) {
      fetchStats();
    }
  }, [initialState?.isLogin]);

  const getTimeGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 6) return '凌晨好';
    if (hour < 12) return '上午好';
    if (hour < 14) return '中午好';
    if (hour < 18) return '下午好';
    return '晚上好';
  };

  return (
    <PageContainer ghost className={styles.dashboardContainer}>
      <Row gutter={[24, 24]}>
        {/* Welcome Card */}
        <Col span={24}>
          <Card className={styles.greetingCard}>
            <h1>{getTimeGreeting()}，欢迎回到 YU Flow</h1>
            <p>基于高性能表达式执行引擎的下一代动态网关与低代码编排平台。</p>
          </Card>
        </Col>

        {/* Metrics Row */}
        <Col xs={24} sm={12} lg={6}>
          <Spin spinning={loading}>
            <Card className={`${styles.metricCard} ${styles.cardApi}`} bordered={false}>
              <div className={styles.metricHeader}>
                <span>动态接口配置</span>
                <div className={styles.metricIcon}>
                  <ApiOutlined />
                </div>
              </div>
              <div className={styles.metricValue}>{stats.apis}</div>
              <div className={styles.metricFooter}>
                <span className={styles.trendUp}><ArrowUpOutlined /> 在线运行中</span>
                <span>核心网关层</span>
              </div>
            </Card>
          </Spin>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Spin spinning={loading}>
            <Card className={`${styles.metricCard} ${styles.cardSource}`} bordered={false}>
              <div className={styles.metricHeader}>
                <span>连接数据源</span>
                <div className={styles.metricIcon}>
                  <CloudServerOutlined />
                </div>
              </div>
              <div className={styles.metricValue}>{stats.sources}</div>
              <div className={styles.metricFooter}>
                <Tag color="blue" bordered={false}>健康状态: 优</Tag>
              </div>
            </Card>
          </Spin>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Spin spinning={loading}>
            <Card className={`${styles.metricCard} ${styles.cardModel}`} bordered={false}>
              <div className={styles.metricHeader}>
                <span>实体数据模型</span>
                <div className={styles.metricIcon}>
                  <DatabaseOutlined />
                </div>
              </div>
              <div className={styles.metricValue}>{stats.models}</div>
              <div className={styles.metricFooter}>
                <Tag color="success" bordered={false}>物理表同步中</Tag>
              </div>
            </Card>
          </Spin>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Spin spinning={loading}>
            <Card className={`${styles.metricCard} ${styles.cardPage}`} bordered={false}>
              <div className={styles.metricHeader}>
                <span>可视低代码页面</span>
                <div className={styles.metricIcon}>
                  <LayoutOutlined />
                </div>
              </div>
              <div className={styles.metricValue}>{stats.pages}</div>
              <div className={styles.metricFooter}>
                <span className={styles.trendUp}>渲染完成</span>
                <span>Amis 引擎</span>
              </div>
            </Card>
          </Spin>
        </Col>

        {/* Engine Status & Quick Actions */}
        <Col xs={24} lg={12}>
          <Card title="引擎执行监控实况" bordered={false} style={{ height: '100%' }}>
            <Row gutter={[32, 0]} align="middle">
              <Col span={10} style={{ textAlign: 'center' }}>
                <Progress 
                  type="dashboard" 
                  percent={99.9} 
                  strokeColor={{ '0%': '#10b981', '100%': '#3b82f6' }}
                  format={percent => `${percent}%`}
                  size={180}
                />
                <div style={{ marginTop: 16, color: '#6b7280', fontWeight: 500 }}>
                  <SafetyCertificateOutlined style={{ marginRight: 8, color: '#10b981' }} />
                  流程执行成功率
                </div>
              </Col>
              
              <Col span={14}>
                <div className={styles.engineStatus}>
                  <div className={styles.statusRow}>
                    <span className={styles.statusLabel}>
                      <FieldTimeOutlined style={{ marginRight: 8, color: '#6366f1' }}/>
                      网关平均耗时
                    </span>
                    <span className={styles.statusValue}>42 ms</span>
                  </div>
                  <div className={styles.statusRow}>
                    <span className={styles.statusLabel}>
                      <ThunderboltOutlined style={{ marginRight: 8, color: '#f59e0b' }}/>
                      当前并发吞吐量
                    </span>
                    <span className={styles.statusValue}>128 QPS</span>
                  </div>
                  <div className={styles.statusRow}>
                    <span className={styles.statusLabel}>
                      <SafetyCertificateOutlined style={{ marginRight: 8, color: '#10b981' }}/>
                      Anti-Hang 防挂死策略
                    </span>
                    <Tag color="success" style={{ margin: 0 }}>Active</Tag>
                  </div>
                </div>
              </Col>
            </Row>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="快捷操作台" bordered={false} style={{ height: '100%' }}>
            <div className={styles.quickActions}>
              <div className={styles.actionBtn} onClick={() => history.push('/flow/controller')}>
                <div className={styles.actionIcon}><CodeOutlined /></div>
                <span className={styles.actionText}>管理动态接口</span>
              </div>
              <div className={styles.actionBtn} onClick={() => history.push('/flow/dataSource')}>
                <div className={styles.actionIcon}><CloudServerOutlined style={{ color: '#3b82f6' }}/></div>
                <span className={styles.actionText}>配置外部数据源</span>
              </div>
              <div className={styles.actionBtn} onClick={() => history.push('/data-model/list')}>
                <div className={styles.actionIcon}><DatabaseOutlined style={{ color: '#10b981' }}/></div>
                <span className={styles.actionText}>设计数据实体</span>
              </div>
              <div className={styles.actionBtn} onClick={() => history.push('/page-manage/list')}>
                <div className={styles.actionIcon}><LayoutOutlined style={{ color: '#f59e0b' }}/></div>
                <span className={styles.actionText}>低代码可视化设计</span>
              </div>
            </div>
          </Card>
        </Col>

      </Row>
    </PageContainer>
  );
};

export default HomePage;

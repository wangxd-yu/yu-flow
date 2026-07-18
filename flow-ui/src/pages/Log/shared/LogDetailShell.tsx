import React from 'react';
import { Alert, Badge, Card, Space, Tag, Tabs, Typography } from 'antd';
import type { TabsProps } from 'antd';
import {
  CheckCircleFilled,
  CloseCircleFilled,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { formatDuration, getDurationColor } from './logFormat';
import './logDetailShell.css';

const { Text, Title } = Typography;

export interface LogDetailShellOverview {
  /** 主标题 */
  title: React.ReactNode;
  /** 左侧彩色图标块 */
  icon?: React.ReactNode;
  iconColor?: string;
  /** 标题下 Tags / 副文案 */
  tags?: React.ReactNode;
  /** 是否成功（undefined 时不展示状态 Tag） */
  success?: boolean;
  successText?: string;
  failText?: string;
  /** 耗时 ms */
  durationMs?: number | null;
  /** 时间文案 */
  timeText?: string;
  /** 右侧额外操作区 */
  extra?: React.ReactNode;
}

export interface LogDetailShellProps {
  overview: LogDetailShellOverview;
  /** 错误信息，有则展示 Alert */
  errorMessage?: string;
  errorTitle?: string;
  /** Tabs items；不传则只渲染概览 */
  tabItems?: TabsProps['items'];
  activeTabKey?: string;
  onTabChange?: (key: string) => void;
  tabBarExtraContent?: React.ReactNode;
  children?: React.ReactNode;
}

/**
 * 日志详情统一外壳：渐变底 + 概览 Card + 错误 Alert + Tabs
 * Tabs 区域自适应填满 Drawer 剩余高度
 */
const LogDetailShell: React.FC<LogDetailShellProps> = ({
  overview,
  errorMessage,
  errorTitle = '调用异常',
  tabItems,
  activeTabKey,
  onTabChange,
  tabBarExtraContent,
  children,
}) => {
  const {
    title,
    icon,
    iconColor = '#1677ff',
    tags,
    success,
    successText = '成功',
    failText = '失败',
    durationMs,
    timeText,
    extra,
  } = overview;

  return (
    <div className="log-detail-shell">
      <Card
        className="log-detail-shell-overview"
        style={{
          marginBottom: 16,
          borderRadius: 12,
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)',
        }}
        bodyStyle={{ padding: '16px 20px' }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
            {icon && (
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 10,
                  background: `linear-gradient(135deg, ${iconColor}20, ${iconColor}40)`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 20,
                  color: iconColor,
                  flexShrink: 0,
                }}
              >
                {icon}
              </div>
            )}
            <div style={{ minWidth: 0 }}>
              <Title level={4} style={{ margin: 0, fontSize: 18 }}>
                {title}
              </Title>
              {tags && (
                <Space size={8} style={{ marginTop: 4 }} wrap>
                  {tags}
                </Space>
              )}
            </div>
          </div>

          <Space size={16} wrap>
            {success === true && (
              <Badge
                status="success"
                text={
                  <Tag
                    icon={<CheckCircleFilled />}
                    color="success"
                    style={{ fontSize: 13, padding: '2px 12px' }}
                  >
                    {successText}
                  </Tag>
                }
              />
            )}
            {success === false && (
              <Badge
                status="error"
                text={
                  <Tag
                    icon={<CloseCircleFilled />}
                    color="error"
                    style={{ fontSize: 13, padding: '2px 12px' }}
                  >
                    {failText}
                  </Tag>
                }
              />
            )}
            {durationMs != null && (
              <span style={{ color: getDurationColor(durationMs), fontWeight: 600 }}>
                <ClockCircleOutlined style={{ marginRight: 4 }} />
                {formatDuration(durationMs)}
              </span>
            )}
            {timeText && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {timeText}
              </Text>
            )}
            {extra}
          </Space>
        </div>
      </Card>

      {errorMessage && (
        <Alert
          className="log-detail-shell-error"
          type="error"
          showIcon
          message={errorTitle}
          description={
            <pre
              style={{
                margin: 0,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 12,
                maxHeight: 160,
                overflow: 'auto',
              }}
            >
              {errorMessage}
            </pre>
          }
          style={{ marginBottom: 16, borderRadius: 10 }}
        />
      )}

      {tabItems && tabItems.length > 0 && (
        <Card className="log-detail-shell-tabs-card" bordered={false}>
          <Tabs
            className="log-detail-shell-tabs"
            activeKey={activeTabKey}
            onChange={onTabChange}
            destroyInactiveTabPane
            tabBarExtraContent={tabBarExtraContent}
            items={tabItems}
          />
        </Card>
      )}

      {children}
    </div>
  );
};

export default React.memo(LogDetailShell);

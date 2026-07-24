import React from 'react';
import { Button, Space, Typography } from 'antd';
import { history } from '@umijs/max';

const { Text } = Typography;

/** deep-link 筛选条：展示当前资产筛选 + 一键清除 */
export const LogDeepLinkBar: React.FC<{
  label: string;
  clearPath: string;
}> = ({ label, clearPath }) => (
  <Space size={8}>
    <Text type="secondary">当前筛选：</Text>
    <Text strong>{label}</Text>
    <Button
      type="link"
      size="small"
      onClick={() => history.push(clearPath)}
      style={{ padding: 0 }}
    >
      清除筛选
    </Button>
  </Space>
);

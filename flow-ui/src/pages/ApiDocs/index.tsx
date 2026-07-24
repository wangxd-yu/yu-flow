import React from 'react';
import { Card, Button, Space, Typography, Divider, message, Alert } from 'antd';
import {
  FileTextOutlined,
  CopyOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import { request } from '@umijs/max';

const { Title, Paragraph, Text } = Typography;

/**
 * OpenAPI 文档页面
 *
 * - 复制 JSON 端点地址（需登录态 Header）
 * - 下载 OpenAPI JSON 文件
 */
const ApiDocsPage: React.FC = () => {
  const apiDocsUrl = `${window.location.origin}/flow-api/v3/api-docs`;

  const handleCopyUrl = () => {
    navigator.clipboard.writeText(apiDocsUrl).then(() => {
      message.success('OpenAPI 端点地址已复制到剪贴板');
    }).catch(() => {
      message.error('复制失败');
    });
  };

  const handleDownload = async () => {
    try {
      const res = await request('/flow-api/v3/api-docs', {
        method: 'GET',
        responseType: 'text',
        skipErrorHandler: true,
      });
      const blob = new Blob([typeof res === 'string' ? res : JSON.stringify(res, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'yu-flow-openapi.json';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      message.success('OpenAPI 文档已下载');
    } catch {
      message.error('下载失败');
    }
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <Typography>
        <Title level={3}>
          <FileTextOutlined style={{ marginRight: 8 }} />
          API 文档中心
        </Title>
        <Paragraph type="secondary">
          Yu Flow 会根据所有已发布的动态接口，实时生成标准 <Text code>OpenAPI 3.0</Text> 契约。
          可下载 JSON 或导入 Postman、Apifox 等工具（浏览器使用 Cookie 会话；工具调试可改用请求头 <Text code>Flow-Authorization: Bearer &lt;jwt&gt;</Text>）。
        </Paragraph>
        <Alert
          type="info"
          showIcon
          message="鉴权说明"
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              <li>管理端浏览器登录后使用 HttpOnly Cookie，无需手动拷贝 Token。</li>
              <li>
                第三方工具可用请求头 <Text code>Flow-Authorization</Text>（带 Bearer 前缀）；头鉴权时不强制 CSRF。
              </li>
            </ul>
          }
        />
      </Typography>

      <Divider />

      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card style={{ borderLeft: '4px solid #52c41a' }}>
          <Space align="start">
            <CopyOutlined style={{ fontSize: 24, color: '#52c41a' }} />
            <div style={{ flex: 1 }}>
              <Text strong style={{ fontSize: 15 }}>OpenAPI 端点地址</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12, marginBottom: 8, display: 'block' }}>
                浏览器已登录即可访问；Postman / Apifox 可配置 Flow-Authorization（头鉴权不强制 CSRF）。
              </Text>
              <Space>
                <Text code copyable={{ text: apiDocsUrl }}>
                  {apiDocsUrl}
                </Text>
                <Button size="small" icon={<CopyOutlined />} onClick={handleCopyUrl}>
                  复制
                </Button>
              </Space>
            </div>
          </Space>
        </Card>

        <Card
          hoverable
          onClick={handleDownload}
          style={{ cursor: 'pointer', borderLeft: '4px solid #faad14' }}
        >
          <Space>
            <DownloadOutlined style={{ fontSize: 24, color: '#faad14' }} />
            <div>
              <Text strong style={{ fontSize: 15 }}>下载 OpenAPI JSON</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>
                下载完整的 OpenAPI 3.0 JSON 文件 (yu-flow-openapi.json)，可用于代码生成或离线浏览。
              </Text>
            </div>
          </Space>
        </Card>
      </Space>
    </div>
  );
};

export default ApiDocsPage;

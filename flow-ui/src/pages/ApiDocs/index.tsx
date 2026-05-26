import React from 'react';
import { Card, Button, Space, Typography, Divider, message } from 'antd';
import {
  FileTextOutlined,
  CopyOutlined,
  LinkOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import { request } from '@umijs/max';

const { Title, Paragraph, Text } = Typography;

/**
 * OpenAPI 文档页面
 *
 * 提供 API 文档的访问入口和导出功能：
 * - 在线 Swagger UI 预览
 * - 复制 JSON 端点地址
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

  const handleOpenSwaggerUi = () => {
    window.open(`${window.location.origin}/flow-api/swagger-ui.html`, '_blank');
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <Typography>
        <Title level={3}>
          <FileTextOutlined style={{ marginRight: 8 }} />
          API 文档中心
        </Title>
        <Paragraph type="secondary">
          Yu Flow 会根据所有已发布的动态接口，实时生成标准 <Text code>OpenAPI 3.0</Text> 契约文档。
          您可以将该文档导入 Postman、Apifox 等工具，或直接通过 Swagger UI 在线浏览。
        </Paragraph>
      </Typography>

      <Divider />

      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {/* Swagger UI */}
        <Card
          hoverable
          onClick={handleOpenSwaggerUi}
          style={{ cursor: 'pointer', borderLeft: '4px solid #667eea' }}
        >
          <Space>
            <LinkOutlined style={{ fontSize: 24, color: '#667eea' }} />
            <div>
              <Text strong style={{ fontSize: 15 }}>在线预览 (Swagger UI)</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>
                在内置的 Swagger UI 界面中浏览所有已发布接口，支持搜索和分组。
              </Text>
            </div>
          </Space>
        </Card>

        {/* 端点地址 */}
        <Card style={{ borderLeft: '4px solid #52c41a' }}>
          <Space align="start">
            <CopyOutlined style={{ fontSize: 24, color: '#52c41a' }} />
            <div style={{ flex: 1 }}>
              <Text strong style={{ fontSize: 15 }}>OpenAPI 端点地址</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12, marginBottom: 8, display: 'block' }}>
                将此 URL 粘贴到 Postman、Apifox 或任何支持 OpenAPI 导入的工具中。
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

        {/* 下载 */}
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

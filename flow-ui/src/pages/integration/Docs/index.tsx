import { PageContainer } from '@ant-design/pro-components';
import { Menu } from 'antd';
import { SafetyCertificateOutlined, CloudUploadOutlined, BookOutlined } from '@ant-design/icons';
import React, { useState, useEffect } from 'react';
import { useSearchParams } from '@umijs/max';
import AuthSpiDoc from './components/AuthSpiDoc';
import OssUploadDoc from './components/OssUploadDoc';

const Docs: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentDoc = searchParams.get('doc') || 'auth-spi';

  const [activeKey, setActiveKey] = useState(currentDoc);

  useEffect(() => {
    setActiveKey(currentDoc);
  }, [currentDoc]);

  const handleMenuClick = (e: any) => {
    setActiveKey(e.key);
    setSearchParams({ doc: e.key });
  };

  const menuItems = [
    {
      key: 'auth-group',
      label: '鉴权与安全集成',
      icon: <SafetyCertificateOutlined />,
      type: 'group',
      children: [
        { key: 'auth-spi', label: '核心用户体系与数据隔离 (SPI)' },
      ],
    },
    {
      key: 'api-group',
      label: '开放接口与组件对接',
      icon: <CloudUploadOutlined />,
      type: 'group',
      children: [
        { key: 'oss-upload', label: 'OSS 文件上传 API' },
      ],
    },
  ];

  return (
    <PageContainer
      header={{
        title: '开发者文档中心',
      }}
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <div
        className="dir-tree-layout"
        style={{
          display: 'flex',
          flex: 1,
          minHeight: 0,
          height: '100%',
          overflow: 'hidden',
          background: '#fff',
          borderRadius: 0,
        }}
      >
        {/* 左侧导航，固定宽度，独立滚动 */}
        <div
          style={{
            width: 220,
            flexShrink: 0,
            height: '100%',
            minHeight: 0,
            overflowY: 'auto',
            borderRight: '1px solid #f0f0f0',
          }}
        >
          <Menu
            mode="inline"
            selectedKeys={[activeKey]}
            onClick={handleMenuClick}
            items={menuItems}
            style={{ borderRight: 'none' }}
          />
        </div>
        {/* 右侧文档内容，flex:1 撑满剩余宽度，独立纵向滚动 */}
        <div
          style={{
            flex: 1,
            height: '100%',
            minHeight: 0,
            overflowY: 'auto',
            padding: '24px 32px',
          }}
        >
          {activeKey === 'auth-spi' && <AuthSpiDoc />}
          {activeKey === 'oss-upload' && <OssUploadDoc />}
        </div>
      </div>
    </PageContainer>
  );
};

export default Docs;

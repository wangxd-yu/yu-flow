/**
 * BasicInfoPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「基础信息」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理接口的元数据（描述、模块、版本、优先级、标签）和返回包装配置
 * 布局：左侧锚点导航 + 右侧内容区，点击导航滚动定位
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Col, Form, Row, Switch } from 'antd';
import type { FormInstance } from 'antd';
import {
  ProForm, ProFormText, ProFormSelect, ProFormDigit, ProFormTextArea,
} from '@ant-design/pro-components';
import { DatabaseOutlined, GiftOutlined, InfoCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import ResponseWrapperSection from './ResponseWrapperSection';
import CacheConfigSection from './CacheConfigSection';
import IngressSecuritySection from './IngressSecuritySection';
import SectionCard from './SectionCard';

// ═══════════════════════════════════════════════════════════════════════════
//  Props / 导航配置
// ═══════════════════════════════════════════════════════════════════════════

export interface BasicInfoPanelProps {
  form: FormInstance;
  /** 来自请求契约的参数提示，用于缓存 Key 配置 */
  paramSuggestions?: Array<{ source: string; name: string }>;
}

const NAV_ITEMS = [
  { key: 'meta', label: '接口元信息', icon: <InfoCircleOutlined /> },
  { key: 'ingress', label: '入站防护', icon: <SafetyCertificateOutlined /> },
  { key: 'cache', label: '查询响应缓存', icon: <DatabaseOutlined /> },
  { key: 'wrapper', label: '返回包装配置', icon: <GiftOutlined /> },
] as const;

type NavKey = (typeof NAV_ITEMS)[number]['key'];

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const BasicInfoPanel: React.FC<BasicInfoPanelProps> = ({ form, paramSuggestions }) => {
  const cacheEnabled = Form.useWatch('cacheEnabled', form);
  const [activeKey, setActiveKey] = useState<NavKey>('meta');
  const scrollingByClick = useRef(false);
  const scrollTimer = useRef<ReturnType<typeof setTimeout>>();
  /** 面板自身的滚动容器，兼作锚点滚动/高亮的 root */
  const scrollRootRef = useRef<HTMLDivElement>(null);

  const scrollToSection = useCallback((key: NavKey) => {
    const el = document.getElementById(`basic-info-${key}`);
    if (!el) return;

    setActiveKey(key);
    scrollingByClick.current = true;
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });

    clearTimeout(scrollTimer.current);
    scrollTimer.current = setTimeout(() => {
      scrollingByClick.current = false;
    }, 600);
  }, []);

  useEffect(() => {
    const ids = NAV_ITEMS.map((item) => `basic-info-${item.key}`);
    const elements = ids
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => !!el);

    if (elements.length === 0) return undefined;

    const root = scrollRootRef.current;
    const observer = new IntersectionObserver(
      (entries) => {
        if (scrollingByClick.current) return;
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
        const top = visible[0];
        if (!top?.target?.id) return;
        const key = top.target.id.replace('basic-info-', '') as NavKey;
        if (NAV_ITEMS.some((item) => item.key === key)) {
          setActiveKey(key);
        }
      },
      {
        root,
        rootMargin: '-12% 0px -55% 0px',
        threshold: [0, 0.25, 0.5, 0.75, 1],
      },
    );

    elements.forEach((el) => observer.observe(el));
    return () => {
      observer.disconnect();
      clearTimeout(scrollTimer.current);
    };
  }, []);

  return (
    <div ref={scrollRootRef} className="basic-info-scroll">
      <style>{`
        /* 面板自身作为滚动容器，撑满 Tab 内容区并可独立滚动 */
        .basic-info-scroll {
          height: 100%;
          overflow-y: auto;
          overflow-x: hidden;
          overscroll-behavior: contain;
        }
        .basic-info-panel {
          display: flex;
          gap: 20px;
          max-width: 1080px;
          margin: 0 auto;
          padding: 16px 4px 32px;
          box-sizing: border-box;
        }
        .basic-info-panel .ant-form-item {
          margin-bottom: 14px;
        }
        .basic-info-panel .ant-form-item-extra {
          min-height: 0;
        }
        .basic-info-nav-btn:hover {
          background: #f2f6fc !important;
        }
      `}</style>

      <div className="basic-info-panel">
        {/* ── 左侧锚点导航 ── */}
        <nav
          style={{
            width: 160,
            flexShrink: 0,
            position: 'sticky',
            top: 0,
            alignSelf: 'flex-start',
          }}
        >
          <div
            style={{
              background: '#fff',
              border: '1px solid #ebeef5',
              borderRadius: 10,
              padding: '6px',
              overflow: 'hidden',
              boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
            }}
          >
            {NAV_ITEMS.map((item) => {
              const active = activeKey === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  className="basic-info-nav-btn"
                  onClick={() => scrollToSection(item.key)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    width: '100%',
                    border: 'none',
                    borderRadius: 8,
                    background: active ? '#e6f4ff' : 'transparent',
                    color: active ? '#1677ff' : '#4e5969',
                    fontWeight: active ? 600 : 400,
                    fontSize: 13,
                    padding: '9px 10px',
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'background 0.2s, color 0.2s',
                  }}
                >
                  <span style={{ fontSize: 14, display: 'flex', color: active ? '#1677ff' : '#86909c' }}>
                    {item.icon}
                  </span>
                  {item.label}
                </button>
              );
            })}
          </div>
        </nav>

        {/* ── 右侧内容区 ── */}
        <div style={{ flex: 1, minWidth: 0 }}>
        <ProForm form={form} submitter={false} layout="vertical">
          <SectionCard
            id="basic-info-meta"
            icon={<InfoCircleOutlined />}
            title="接口元信息"
          >
            <Row gutter={[16, 0]}>
              <Col span={24}>
                <ProFormTextArea
                  name="info"
                  label="描述"
                  placeholder="请输入接口描述，描述该接口的用途和注意事项"
                  fieldProps={{ autoSize: { minRows: 2, maxRows: 4 } }}
                />
              </Col>

              <Col span={12}>
                <ProFormText
                  name="module"
                  label="模块"
                  placeholder="如: user、order"
                />
              </Col>
              <Col span={12}>
                <DirectoryTreeSelect
                  bizType="api"
                  name="directoryId"
                  label="所属目录"
                  placeholder="不选默认为根目录"
                />
              </Col>

              <Col span={12}>
                <ProFormText
                  name="version"
                  label="版本"
                  placeholder="如: v1、v2"
                />
              </Col>
              <Col span={12}>
                <ProFormDigit
                  name="level"
                  label="优先级"
                  placeholder="1-10"
                  min={1}
                  max={10}
                  fieldProps={{ precision: 0, style: { width: '100%' } }}
                />
              </Col>

              <Col span={24}>
                <ProFormSelect
                  name="tags"
                  label="标签"
                  mode="tags"
                  placeholder="输入后回车添加标签"
                  fieldProps={{ maxTagCount: 5, tokenSeparators: [','] }}
                  formItemProps={{ style: { marginBottom: 0 } }}
                />
              </Col>
            </Row>
          </SectionCard>

          <SectionCard
            id="basic-info-ingress"
            tone="primary"
            icon={<SafetyCertificateOutlined />}
            title="入站防护"
            description="鉴权 / 防重放 / 限流 / IP；继承全局或按接口覆盖，需发布后生效"
          >
            <IngressSecuritySection />
          </SectionCard>

          <SectionCard
            id="basic-info-cache"
            tone="primary"
            icon={<DatabaseOutlined />}
            title="查询响应缓存"
            description="开启后按选定入参缓存响应；需保存草稿后生效（已发布接口保存即可）"
            bodyVisible={!!cacheEnabled}
            extra={
              <Form.Item name="cacheEnabled" valuePropName="checked" noStyle>
                <Switch checkedChildren="开" unCheckedChildren="关" />
              </Form.Item>
            }
          >
            <CacheConfigSection form={form} paramSuggestions={paramSuggestions} />
          </SectionCard>

          <SectionCard
            id="basic-info-wrapper"
            icon={<GiftOutlined />}
            title="返回包装配置"
            description="选择基座模板并可通过开关进行局部重载"
            style={{ marginBottom: 0 }}
          >
            <ResponseWrapperSection form={form} />
          </SectionCard>
        </ProForm>
        </div>
      </div>
    </div>
  );
};

export default React.memo(BasicInfoPanel);

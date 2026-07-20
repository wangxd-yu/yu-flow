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
import { DatabaseOutlined, GiftOutlined, InfoCircleOutlined } from '@ant-design/icons';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import ResponseWrapperSection from './ResponseWrapperSection';
import CacheConfigSection from './CacheConfigSection';
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
  { key: 'cache', label: '查询响应缓存', icon: <DatabaseOutlined /> },
  { key: 'wrapper', label: '返回包装配置', icon: <GiftOutlined /> },
] as const;

type NavKey = (typeof NAV_ITEMS)[number]['key'];

const SCROLL_ROOT_SELECTOR =
  '.controller-form-page-container > .ant-pro-grid-content';

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const BasicInfoPanel: React.FC<BasicInfoPanelProps> = ({ form, paramSuggestions }) => {
  const cacheEnabled = Form.useWatch('cacheEnabled', form);
  const [activeKey, setActiveKey] = useState<NavKey>('meta');
  const scrollingByClick = useRef(false);
  const scrollTimer = useRef<ReturnType<typeof setTimeout>>();

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

    const root = document.querySelector(SCROLL_ROOT_SELECTOR) as Element | null;
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
    <div
      className="basic-info-panel"
      style={{ display: 'flex', gap: 20, maxWidth: 1080, margin: '0 auto', padding: '16px 0 24px' }}
    >
      <style>{`
        .basic-info-panel .ant-form-item {
          margin-bottom: 14px;
        }
        .basic-info-panel .ant-form-item-extra {
          min-height: 0;
        }
      `}</style>

      {/* ── 左侧锚点导航 ── */}
      <nav
        style={{
          width: 156,
          flexShrink: 0,
          position: 'sticky',
          top: 12,
          alignSelf: 'flex-start',
        }}
      >
        <div
          style={{
            background: '#fff',
            border: '1px solid #ebeef5',
            borderRadius: 8,
            padding: '6px 0',
            overflow: 'hidden',
          }}
        >
          {NAV_ITEMS.map((item) => {
            const active = activeKey === item.key;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => scrollToSection(item.key)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  width: '100%',
                  border: 'none',
                  borderLeft: active ? '3px solid #1677ff' : '3px solid transparent',
                  background: active ? '#e6f4ff' : 'transparent',
                  color: active ? '#1677ff' : '#4e5969',
                  fontWeight: active ? 600 : 400,
                  fontSize: 13,
                  padding: '9px 12px 9px 10px',
                  cursor: 'pointer',
                  textAlign: 'left',
                  transition: 'background 0.2s, color 0.2s',
                }}
              >
                <span style={{ fontSize: 14, display: 'flex' }}>{item.icon}</span>
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
  );
};

export default React.memo(BasicInfoPanel);

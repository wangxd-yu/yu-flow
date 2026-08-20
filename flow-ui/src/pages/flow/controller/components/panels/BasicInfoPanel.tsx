/**
 * BasicInfoPanel.tsx
 * ─────────────────────────────────────────────────────────────────────────────
 * 「基础信息」面板 — 从 ControllerForm God Component 中提取
 *
 * 职责：管理接口的元数据（描述、模块、版本、优先级、标签）和返回包装配置
 * 布局：左侧锚点导航 + 右侧内容区，点击导航滚动定位
 * ─────────────────────────────────────────────────────────────────────────────
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Col, Form, Row, Switch, Radio, Tooltip, Divider } from 'antd';
import type { FormInstance } from 'antd';
import {
  ProForm, ProFormText, ProFormSelect, ProFormDigit, ProFormTextArea,
} from '@ant-design/pro-components';
import { SafetyCertificateOutlined, DatabaseOutlined, GiftOutlined, InfoCircleOutlined } from '@ant-design/icons';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import {
  ASSET_FORM_SCROLL_CLASS,
  ASSET_FORM_BASIC_CLASS,
  ASSET_FORM_COL_FIELD,
  ASSET_FORM_COL_FULL,
} from '@/components/flow/ops';
import { useGlobalLogMode, getLogModeLabel } from '@/components/flow/useGlobalLogMode';
import ResponseWrapperSection from './ResponseWrapperSection';
import CacheConfigSection from './CacheConfigSection';
import IngressSecuritySection from './IngressSecuritySection';
import PrivacySection from './PrivacySection';
import SectionCard from './SectionCard';

// ═══════════════════════════════════════════════════════════════════════════
//  Props / 导航配置
// ═══════════════════════════════════════════════════════════════════════════

export interface BasicInfoPanelProps {
  form: FormInstance;
  /** 来自请求契约的参数提示，用于缓存 Key 配置 */
  paramSuggestions?: Array<{ source: string; name: string }>;
  /** 同名拦截模式；WRAP 时隐藏返回包装（透传宿主响应） */
  interceptMode?: 'REPLACE' | 'WRAP' | string;
}

const ALL_NAV_ITEMS = [
  { key: 'meta', label: '接口元信息', icon: <InfoCircleOutlined /> },
  { key: 'access', label: '访问控制', icon: <SafetyCertificateOutlined /> },
  { key: 'cache', label: '查询响应缓存', icon: <DatabaseOutlined /> },
  { key: 'wrapper', label: '返回包装配置', icon: <GiftOutlined /> },
] as const;

type NavKey = (typeof ALL_NAV_ITEMS)[number]['key'];

// ═══════════════════════════════════════════════════════════════════════════
//  组件实现
// ═══════════════════════════════════════════════════════════════════════════

const BasicInfoPanel: React.FC<BasicInfoPanelProps> = ({
  form, paramSuggestions, interceptMode = 'REPLACE',
}) => {
  const isWrap = interceptMode === 'WRAP';
  const navItems = useMemo(
    () => (isWrap
      ? ALL_NAV_ITEMS.filter((i) => i.key !== 'wrapper' && i.key !== 'cache')
      : [...ALL_NAV_ITEMS]),
    [isWrap],
  );
  const cacheEnabled = Form.useWatch('cacheEnabled', form);
  const logMode = Form.useWatch('logMode', form);
  const globalLogMode = useGlobalLogMode();
  const [activeKey, setActiveKey] = useState<NavKey>('meta');
  const scrollingByClick = useRef(false);
  const scrollTimer = useRef<ReturnType<typeof setTimeout>>();
  /** 面板自身的滚动容器，兼作锚点滚动/高亮的 root */
  const scrollRootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isWrap && (activeKey === 'wrapper' || activeKey === 'cache')) {
      setActiveKey('meta');
    }
  }, [isWrap, activeKey]);

  // WRAP 透传宿主响应，强制关闭缓存开关，避免误以为会缓存宿主结果
  useEffect(() => {
    if (isWrap && form.getFieldValue('cacheEnabled')) {
      form.setFieldsValue({ cacheEnabled: false });
    }
  }, [isWrap, form]);

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
    const ids = navItems.map((item) => `basic-info-${item.key}`);
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
        if (navItems.some((item) => item.key === key)) {
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
  }, [navItems]);

  const logModeHint = useMemo(() => {
    if (!logMode || logMode === 'SYSTEM_DEFAULT') {
      return `继承全局策略：当前全局生效为【${getLogModeLabel(globalLogMode)}】（来自系统配置 ENGINE_DEFAULT_LOG_MODE）`;
    }
    if (logMode === 'ERROR_ONLY') {
      return '覆盖全局配置：显式指定当前接口为【仅错误】，平时零开销，异常自动保存日志排障';
    }
    if (logMode === 'ALL') {
      return '覆盖全局配置：显式指定当前接口为【全量记录】，每次调用均保存 FlowTrace 快照';
    }
    if (logMode === 'OFF') {
      return '覆盖全局配置：显式指定当前接口为【完全关闭】，任何情况下均不保存日志';
    }
    return undefined;
  }, [logMode, globalLogMode]);

  return (
    <div ref={scrollRootRef} className={ASSET_FORM_SCROLL_CLASS}>
      <style>{`
        .basic-info-panel {
          display: flex;
          align-items: flex-start;
          gap: 20px;
          width: 100%;
          box-sizing: border-box;
        }
        .basic-info-panel .ant-form-item-extra {
          min-height: 0;
        }
        .basic-info-nav {
          width: 168px;
          flex-shrink: 0;
          position: sticky;
          top: 0;
        }
        .basic-info-nav-inner {
          background: #fff;
          border: 1px solid #ebeef5;
          border-radius: 10px;
          padding: 6px;
          box-shadow: 0 1px 2px rgba(0,0,0,0.03);
        }
        .basic-info-nav-btn {
          display: flex;
          align-items: center;
          gap: 8px;
          width: 100%;
          border: none;
          border-left: 3px solid transparent;
          border-radius: 6px;
          background: transparent;
          color: #4e5969;
          font-weight: 400;
          font-size: 13px;
          line-height: 22px;
          padding: 8px 10px;
          cursor: pointer;
          text-align: left;
          transition: background 0.2s, color 0.2s, border-color 0.2s;
        }
        .basic-info-nav-btn:hover {
          background: #f2f6fc;
        }
        .basic-info-nav-btn.is-active {
          border-left-color: #1677ff;
          border-radius: 0 6px 6px 0;
          background: #e6f4ff;
          color: #1677ff;
          font-weight: 600;
        }
        .basic-info-nav-btn.is-active:hover {
          background: #e6f4ff;
        }
        .basic-info-nav-icon {
          display: flex;
          font-size: 14px;
          color: #86909c;
        }
        .basic-info-nav-btn.is-active .basic-info-nav-icon {
          color: #1677ff;
        }
        .basic-info-content {
          flex: 1;
          min-width: 0;
        }
        @media (max-width: 900px) {
          .basic-info-panel {
            flex-direction: column;
          }
          .basic-info-nav {
            width: 100%;
            position: static;
          }
          .basic-info-nav-inner {
            display: flex;
            gap: 4px;
          }
          .basic-info-nav-btn {
            flex: 1;
            justify-content: center;
            border-left: none;
            border-bottom: 2px solid transparent;
            border-radius: 6px;
            padding: 8px 6px;
          }
          .basic-info-nav-btn.is-active {
            border-left: none;
            border-bottom-color: #1677ff;
            border-radius: 6px;
          }
        }
      `}</style>

      <div className="basic-info-panel">
        <nav className="basic-info-nav" aria-label="基本信息分区">
          <div className="basic-info-nav-inner">
            {navItems.map((item) => {
              const active = activeKey === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  className={`basic-info-nav-btn${active ? ' is-active' : ''}`}
                  aria-current={active ? 'true' : undefined}
                  onClick={() => scrollToSection(item.key)}
                >
                  <span className="basic-info-nav-icon">{item.icon}</span>
                  {item.label}
                </button>
              );
            })}
          </div>
        </nav>

        <div className={`basic-info-content ${ASSET_FORM_BASIC_CLASS}`}>
        <ProForm form={form} submitter={false} layout="vertical">
          <SectionCard
            id="basic-info-meta"
            icon={<InfoCircleOutlined />}
            title="接口元信息"
          >
            <Row gutter={[24, 0]} className="yf-asset-form-row">
              <Col {...ASSET_FORM_COL_FULL}>
                <ProFormTextArea
                  name="info"
                  label="描述"
                  placeholder="请输入接口描述，描述该接口的用途和注意事项"
                  fieldProps={{ autoSize: { minRows: 2, maxRows: 4 } }}
                />
              </Col>

              <Col {...ASSET_FORM_COL_FIELD}>
                <ProFormText
                  name="module"
                  label="模块"
                  placeholder="如: user、order"
                />
              </Col>
              <Col {...ASSET_FORM_COL_FIELD}>
                <DirectoryTreeSelect
                  bizType="api"
                  name="directoryId"
                  label="所属目录"
                  placeholder="不选默认为根目录"
                />
              </Col>
              <Col {...ASSET_FORM_COL_FIELD}>
                <ProFormText
                  name="version"
                  label="版本"
                  placeholder="如: v1、v2"
                />
              </Col>

              <Col {...ASSET_FORM_COL_FIELD}>
                <ProFormDigit
                  name="level"
                  label="优先级"
                  placeholder="1-10"
                  min={1}
                  max={10}
                  fieldProps={{ precision: 0, style: { width: '100%' } }}
                />
              </Col>
              <Col {...ASSET_FORM_COL_FIELD}>
                <ProFormDigit
                  name="logRetentionDays"
                  label="日志保留天数"
                  placeholder="留空跟随系统配置"
                  min={0}
                  tooltip="留空=跟随系统配置（LOG_EXECUTION_RETENTION_DAYS），0=永久保留，>0=按天数自动清理执行日志"
                  fieldProps={{ precision: 0, style: { width: '100%' } }}
                />
              </Col>
              <Col {...ASSET_FORM_COL_FULL}>
                <Form.Item
                  name="logMode"
                  label="日志策略"
                  extra={logModeHint}
                >
                  <Radio.Group optionType="button" buttonStyle="solid">
                    <Tooltip title={`跟随系统全局配置（当前全局：${getLogModeLabel(globalLogMode)}，可在「系统配置」中热更）`}>
                      <Radio.Button value="SYSTEM_DEFAULT">继承全局</Radio.Button>
                    </Tooltip>
                    <Tooltip title="显式指定当前接口仅在发生报错/失败时记录日志">
                      <Radio.Button value="ERROR_ONLY">仅错误</Radio.Button>
                    </Tooltip>
                    <Tooltip title="显式指定当前接口全量记录成功与失败日志（含 FlowTrace 快照）">
                      <Radio.Button value="ALL">全量记录</Radio.Button>
                    </Tooltip>
                    <Tooltip title="显式指定当前接口完全禁用日志记录，任何情况下均不落库">
                      <Radio.Button value="OFF">完全关闭</Radio.Button>
                    </Tooltip>
                  </Radio.Group>
                </Form.Item>
              </Col>

              <Col {...ASSET_FORM_COL_FULL}>
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
            id="basic-info-access"
            icon={<SafetyCertificateOutlined />}
            title="访问控制"
            description="入站（谁能调）与出站（谁看什么）同一页配置，身份勾选与 OSS 访问规则相同"
          >
            <IngressSecuritySection />
            <Divider orientation="left" plain>
              出站隐私
            </Divider>
            <PrivacySection />
          </SectionCard>

          {isWrap ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message="当前为「包裹模式」：查询缓存与返回包装已自动停用"
              description="包裹模式专注于透传宿主的原始响应。查询缓存与响应包装功能已在「替换模式」中完整支持；若需启用缓存或统一响应壳，请在右上角切换为「替换宿主」。"
            />
          ) : (
            <>
              <SectionCard
                id="basic-info-cache"
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
            </>
          )}
        </ProForm>
        </div>
      </div>
    </div>
  );
};

export default React.memo(BasicInfoPanel);

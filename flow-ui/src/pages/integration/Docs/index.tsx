import { PageContainer } from '@ant-design/pro-components';
import { Menu } from 'antd';
import type { MenuProps } from 'antd';
import { SafetyCertificateOutlined, CloudUploadOutlined } from '@ant-design/icons';
import React, { useEffect, useRef, useState } from 'react';
import { useModel, useSearchParams } from '@umijs/max';
import AuthSpiDoc from './components/AuthSpiDoc';
import OssUploadDoc from './components/OssUploadDoc';
// 全高布局样式必须由本页自行引入：路由级代码分割下，不引入则首屏直达本页时 .fh-container 规则缺失，
// PageContainer 不会变成 flex 容器，右侧文档区拿不到 flex:1 高度，内容被 overflow:hidden 截断且无滚动条
import '@/styles/fullHeightTable.css';

/** 章节 id 为 'top' 时代表文档开头，不落到 URL，也不做锚点查找 */
const TOP_SECTION = 'top';

interface DocSection {
  /** 与文档组件里 <Title id="..."> 一致；'top' 为约定的文档开头 */
  id: string;
  label: string;
}

interface DocMeta {
  key: string;
  label: string;
  icon: React.ReactNode;
  Component: React.FC;
  sections: DocSection[];
}

interface DocGroup {
  key: string;
  label: string;
  docs: DocMeta[];
}

/** 目录树数据源：新增文档/章节只需在此登记，菜单与右侧渲染自动跟随 */
const DOC_GROUPS: DocGroup[] = [
  {
    key: 'auth-group',
    label: '鉴权与安全集成',
    docs: [
      {
        key: 'auth-spi',
        label: '核心用户体系与数据隔离 (SPI)',
        icon: <SafetyCertificateOutlined />,
        Component: AuthSpiDoc,
        sections: [
          { id: TOP_SECTION, label: '文档概览' },
          { id: 'auth-dual', label: '1. 双层鉴权' },
          { id: 'auth-principal', label: '2. 用户体系对接' },
          { id: 'auth-catalog', label: '2.1 身份目录清单' },
          { id: 'auth-caller-policy', label: '3. 调用方策略' },
          { id: 'auth-oss-caller', label: '3.1 OSS 宿主策略' },
          { id: 'auth-privacy', label: '3.2 接口出站隐私' },
          { id: 'auth-privacy-mask', label: '3.2.1 脱敏规则' },
          { id: 'auth-privacy-who', label: '3.2.2 谁看什么' },
          { id: 'auth-privacy-transport', label: '3.2.3 传输封装' },
          { id: 'auth-privacy-host', label: '3.2.4 宿主接入' },
          { id: 'auth-datascope', label: '4. 数据隔离与权限' },
          { id: 'auth-frontend', label: '5. 前端与菜单融合' },
        ],
      },
    ],
  },
  {
    key: 'api-group',
    label: '开放接口与组件对接',
    docs: [
      {
        key: 'oss-upload',
        label: 'OSS 文件上传 API',
        icon: <CloudUploadOutlined />,
        Component: OssUploadDoc,
        sections: [
          { id: TOP_SECTION, label: '文档概览' },
          { id: 'oss-single', label: '1. 单文件上传' },
          { id: 'oss-batch', label: '2. 批量上传' },
          { id: 'oss-multipart', label: '3. 大文件分片上传' },
          { id: 'oss-presign', label: '4. 预签名直传' },
          { id: 'oss-caller', label: '5. 访问规则' },
        ],
      },
    ],
  },
];

const ALL_DOCS = DOC_GROUPS.flatMap((group) => group.docs);
const DEFAULT_DOC = ALL_DOCS[0].key;

function visibleDocGroups(ossEnabled: boolean): DocGroup[] {
  return DOC_GROUPS.map((group) => ({
    ...group,
    docs: ossEnabled ? group.docs : group.docs.filter((doc) => doc.key !== 'oss-upload'),
  })).filter((group) => group.docs.length > 0);
}

/** 父节点（文档）与叶子节点（章节）的 key 编码，避免两级 key 撞车 */
const docNodeKey = (docKey: string) => `doc:${docKey}`;
const sectionNodeKey = (docKey: string, sectionId: string) => `${docKey}#${sectionId}`;

const Docs: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { initialState } = useModel('@@initialState');
  const ossEnabled = initialState?.ossEnabled !== false;
  const groups = visibleDocGroups(ossEnabled);
  const allDocs = groups.flatMap((group) => group.docs);
  const defaultDoc = allDocs[0]?.key || DEFAULT_DOC;

  const docParam = searchParams.get('doc');
  const activeDocKey = allDocs.some((d) => d.key === docParam) ? (docParam as string) : defaultDoc;
  const activeDoc = allDocs.find((d) => d.key === activeDocKey) || allDocs[0];

  const sectionParam = searchParams.get('sec');
  const activeSectionId = activeDoc?.sections.some((s) => s.id === sectionParam)
    ? (sectionParam as string)
    : TOP_SECTION;

  const contentRef = useRef<HTMLDivElement>(null);
  const [openKeys, setOpenKeys] = useState<string[]>(() => ALL_DOCS.map((d) => docNodeKey(d.key)));

  // 切换文档时保证其子节点可见（用户手动收起后再点该文档，需要重新展开）
  useEffect(() => {
    setOpenKeys((prev) =>
      prev.includes(docNodeKey(activeDocKey)) ? prev : [...prev, docNodeKey(activeDocKey)],
    );
  }, [activeDocKey]);

  // 锚点定位：文档组件切换后需等一帧挂载完成才能拿到目标节点
  useEffect(() => {
    const container = contentRef.current;
    if (!container) {
      return;
    }
    const raf = requestAnimationFrame(() => {
      if (activeSectionId === TOP_SECTION) {
        container.scrollTo({ top: 0 });
        return;
      }
      const target = container.querySelector(`#${activeSectionId}`);
      if (!target) {
        return;
      }
      const top =
        target.getBoundingClientRect().top -
        container.getBoundingClientRect().top +
        container.scrollTop;
      container.scrollTo({ top: Math.max(top - 8, 0), behavior: 'smooth' });
    });
    return () => cancelAnimationFrame(raf);
  }, [activeDocKey, activeSectionId]);

  const gotoSection = (docKey: string, sectionId: string) => {
    setSearchParams(
      sectionId === TOP_SECTION ? { doc: docKey } : { doc: docKey, sec: sectionId },
      // 同文档内跳章节只是滚动定位，不该占用浏览器历史；切换文档才留一条记录
      { replace: docKey === activeDocKey },
    );
  };

  // 不做 useMemo：节点数量极少，且 gotoSection 闭包了 activeDocKey，缓存会读到过期值
  const menuItems: MenuProps['items'] = groups.map((group) => ({
    key: group.key,
    label: group.label,
    type: 'group' as const,
    children: group.docs.map((doc) => ({
      key: docNodeKey(doc.key),
      label: doc.label,
      icon: doc.icon,
      // 点父节点标题：切到该文档并回到开头（antd 的 SubMenu 标题本身不参与 selectedKeys）
      onTitleClick: () => gotoSection(doc.key, TOP_SECTION),
      children: doc.sections.map((section) => ({
        key: sectionNodeKey(doc.key, section.id),
        label: section.label,
      })),
    })),
  }));

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    const [docKey, sectionId] = key.split('#');
    if (!sectionId) {
      return;
    }
    gotoSection(docKey, sectionId);
  };

  const ActiveDocComponent = activeDoc?.Component;

  return (
    <PageContainer
      header={{
        title: '集成文档',
      }}
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
    >
      <div
        className="dir-tree-layout"
        style={{
          display: 'flex',
          flexDirection: 'row',
          flex: 1,
          minHeight: 0,
          overflow: 'hidden',
          background: '#fff',
          borderRadius: 0,
        }}
      >
        {/* 左侧目录树，固定宽度，独立滚动 */}
        <div
          style={{
            width: 248,
            flexShrink: 0,
            minHeight: 0,
            overflowY: 'auto',
            borderRight: '1px solid #f0f0f0',
          }}
        >
          <Menu
            mode="inline"
            selectedKeys={activeDoc ? [sectionNodeKey(activeDocKey, activeSectionId)] : []}
            openKeys={openKeys}
            onOpenChange={(keys) => setOpenKeys(keys as string[])}
            onClick={handleMenuClick}
            items={menuItems}
            inlineIndent={16}
            style={{ borderRight: 'none' }}
          />
        </div>
        {/* 右侧文档内容，flex:1 撑满剩余宽度，独立纵向滚动 */}
        <div
          ref={contentRef}
          style={{
            flex: 1,
            minHeight: 0,
            overflowY: 'auto',
            padding: '24px 32px',
          }}
        >
          {ActiveDocComponent ? <ActiveDocComponent /> : null}
        </div>
      </div>
    </PageContainer>
  );
};

export default Docs;

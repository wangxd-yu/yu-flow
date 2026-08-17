import ApiConfigForm from '@/pages/flow/controller/components/ControllerForm';
import { queryAutoApiConfigDetail } from '@/services/flow/flowController';
import {
  getHostCatalogOverview,
  previewHostCatalog,
  saveHostCatalogSettings,
  type HostCatalogApiMeta,
  type HostCatalogDimBinding,
  type HostCatalogOverview,
} from '@/services/flow/hostConfig';
import { resetHostIdentityCatalogCache, type HostCatalogDimension } from '@/services/flow/hostIdentityCatalog';
import {
  IdcardOutlined,
  LockOutlined,
  SaveOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Access, useAccess, useSearchParams } from '@umijs/max';
import { Button, Menu, Space, Tag, message } from 'antd';
import type { MenuProps } from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import '@/styles/fullHeightTable.css';
import HostCatalogPane, {
  HOST_CATALOG_DIMS,
  emptyBinding,
  type CatalogDimRow,
} from './HostCatalogPane';
import './IdentityCatalog.less';
import PrincipalResolverCard from './PrincipalResolver';
import PrivacyProfilesCard from './PrivacyProfiles';

type HostTab = 'principal' | 'privacy' | 'catalog';

const TAB_HINT: Record<HostTab, string> = {
  principal: '识别当前请求是谁，供数据范围与隐私档使用',
  privacy: '库内解密算法、密钥与展示脱敏规则',
  catalog: '声明宿主身份维度及其数据来源',
};

function parseTab(raw: string | null): HostTab {
  if (raw === 'privacy' || raw === 'catalog') return raw;
  return 'principal';
}

function parseDim(raw: string | null): HostCatalogDimension | null {
  return HOST_CATALOG_DIMS.some((d) => d.key === raw)
    ? (raw as HostCatalogDimension)
    : null;
}

const IdentityCatalogPage: React.FC = () => {
  const access = useAccess();
  const permission = access as {
    canHostWrite?: boolean;
    canApiWrite?: boolean;
  };
  const canWrite = !!permission.canHostWrite;
  const canCompose = canWrite && !!permission.canApiWrite;
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = parseTab(searchParams.get('tab'));
  const activeDim = tab === 'catalog' ? parseDim(searchParams.get('dim')) : null;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [spiOverride, setSpiOverride] = useState(false);
  const [settings, setSettings] = useState<Record<string, HostCatalogDimBinding>>({});
  const [apis, setApis] = useState<Record<string, HostCatalogApiMeta>>({});
  const [rawPreview, setRawPreview] = useState<Record<string, string>>({});
  const [formOpen, setFormOpen] = useState(false);
  const [formApi, setFormApi] = useState<Record<string, any> | null>(null);
  const [principalRefresh, setPrincipalRefresh] = useState(0);
  const [openKeys, setOpenKeys] = useState<string[]>(['catalog-menu']);

  useEffect(() => {
    if (tab === 'catalog') {
      setOpenKeys((prev) =>
        prev.includes('catalog-menu') ? prev : [...prev, 'catalog-menu'],
      );
    }
  }, [tab]);

  const applyOverview = useCallback(
    (data: HostCatalogOverview, opts?: { settings?: boolean; apis?: boolean }) => {
      const touchSettings = opts?.settings !== false;
      const touchApis = opts?.apis !== false;
      setSpiOverride(!!data.spiOverride);
      if (touchSettings) {
        const next: Record<string, HostCatalogDimBinding> = {};
        for (const d of HOST_CATALOG_DIMS) {
          next[d.key] = { ...emptyBinding(), ...(data.settings?.[d.key] || {}) };
        }
        setSettings(next);
      }
      if (touchApis) {
        setApis(data.apis || {});
      }
    },
    [],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      applyOverview(await getHostCatalogOverview());
      setDirty(false);
    } catch {
      message.error('加载宿主机配置失败');
    } finally {
      setLoading(false);
    }
  }, [applyOverview]);

  const reloadApis = useCallback(async () => {
    try {
      applyOverview(await getHostCatalogOverview(), { settings: false, apis: true });
    } catch {
      /* 发布状态刷新失败不覆盖绑定 */
    }
  }, [applyOverview]);

  useEffect(() => {
    load();
  }, [load]);

  const persistSettings = useCallback(
    async (next: Record<string, HostCatalogDimBinding>) => {
      const saved = await saveHostCatalogSettings(next);
      applyOverview(saved);
      resetHostIdentityCatalogCache();
      setRawPreview({});
      setDirty(false);
      return saved;
    },
    [applyOverview],
  );

  const patch = useCallback(
    (key: HostCatalogDimension, partial: Partial<HostCatalogDimBinding>) => {
      setSettings((prev) => ({
        ...prev,
        [key]: { ...emptyBinding(), ...prev[key], ...partial },
      }));
      setDirty(true);
    },
    [],
  );

  const handleSave = async () => {
    setSaving(true);
    try {
      await persistSettings(settings);
      message.success('已保存。未启用的维度会从策略表单隐藏，运行时也不匹配');
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const goto = useCallback(
    (nextTab: HostTab, dim?: HostCatalogDimension | null) => {
      const params: Record<string, string> = { tab: nextTab };
      if (nextTab === 'catalog' && dim) params.dim = dim;
      setSearchParams(params, { replace: true });
    },
    [setSearchParams],
  );

  const openEditor = useCallback(
    async (meta?: HostCatalogApiMeta) => {
      if (!meta?.id) {
        message.warning('保留接口尚未创建，请重启后端后再试');
        return;
      }
      const hide = message.loading('正在打开编排', 0);
      try {
        if (dirty) {
          await persistSettings(settings);
        }
        const res: any = await queryAutoApiConfigDetail(meta.id);
        const detail = res?.data && typeof res.data === 'object' ? res.data : res;
        hide();
        setFormApi({
          ...detail,
          id: meta.id,
          name: detail?.name || meta.name,
          url: detail?.url || meta.url,
          systemReserved: true,
        });
        setFormOpen(true);
      } catch {
        hide();
        message.error('保存绑定或获取接口详情失败');
      }
    },
    [dirty, persistSettings, settings],
  );

  const loadRaw = useCallback(async (dim: HostCatalogDimension) => {
    try {
      const items = await previewHostCatalog(dim, '', 50);
      setRawPreview((prev) => ({ ...prev, [dim]: JSON.stringify(items, null, 2) }));
    } catch {
      setRawPreview((prev) => ({ ...prev, [dim]: '[]' }));
    }
  }, []);

  const writeDisabled = !canWrite;

  const rows: CatalogDimRow[] = useMemo(
    () =>
      HOST_CATALOG_DIMS.map((d) => ({
        ...d,
        bind: settings[d.key] || emptyBinding(),
        meta: apis[d.key],
      })),
    [settings, apis],
  );

  const selectedKeys = useMemo(() => {
    if (tab === 'catalog') return [activeDim || 'catalog'];
    return [tab];
  }, [tab, activeDim]);

  const menuItems: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'principal',
        icon: <IdcardOutlined />,
        label: '当前用户解析',
      },
      {
        key: 'privacy',
        icon: <LockOutlined />,
        label: '解密与脱敏方案',
      },
      {
        key: 'catalog-menu',
        icon: <TeamOutlined />,
        label: '身份目录',
        onTitleClick: () => goto('catalog'),
        children: [
          { key: 'catalog', label: '维度总览' },
          ...HOST_CATALOG_DIMS.map((d) => ({
            key: d.key,
            label: (
              <span className="host-nav-item">
                {d.title}
                {settings[d.key]?.enabled ? <i className="host-nav-dot" /> : null}
              </span>
            ),
          })),
        ],
      },
    ],
    [settings, goto],
  );

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'principal' || key === 'privacy' || key === 'catalog') {
      goto(key);
      return;
    }
    if (HOST_CATALOG_DIMS.some((d) => d.key === key)) {
      goto('catalog', key as HostCatalogDimension);
    }
  };

  return (
    <PageContainer
      className="fh-container host-catalog-page"
      loading={loading}
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{
        title: '宿主机配置',
        subTitle: TAB_HINT[tab],
      }}
      extra={
        <Access accessible={!writeDisabled} fallback={null}>
          {tab === 'catalog' || dirty ? (
            <Space>
              {dirty ? <Tag color="processing">身份目录有未保存更改</Tag> : null}
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                disabled={!dirty}
                onClick={handleSave}
              >
                保存目录
              </Button>
            </Space>
          ) : null}
        </Access>
      }
    >
      <div className="host-config-layout">
        <aside className="host-config-nav">
          <Menu
            mode="inline"
            selectedKeys={selectedKeys}
            openKeys={openKeys}
            onOpenChange={(keys) => setOpenKeys(keys as string[])}
            onClick={handleMenuClick}
            items={menuItems}
            inlineIndent={16}
          />
        </aside>
        <div className={`host-config-main ${tab === 'catalog' && !activeDim ? 'is-overview' : 'is-scroll'}`}>
          <div className="host-config-pane" hidden={tab !== 'principal'}>
            <PrincipalResolverCard
              canWrite={canWrite}
              canCompose={canCompose}
              onCompose={openEditor}
              refreshToken={principalRefresh}
            />
          </div>
          <div className="host-config-pane" hidden={tab !== 'privacy'}>
            <PrivacyProfilesCard canWrite={canWrite} />
          </div>
          {tab === 'catalog' ? (
            <HostCatalogPane
              rows={rows}
              activeDim={activeDim}
              spiOverride={spiOverride}
              dirty={dirty}
              canCompose={canCompose}
              writeDisabled={writeDisabled}
              rawPreview={rawPreview}
              onSelectDim={(dim) => goto('catalog', dim)}
              patch={patch}
              openEditor={openEditor}
              loadRaw={loadRaw}
            />
          ) : null}
        </div>
      </div>

      <ApiConfigForm
        isEdit
        modalVisible={formOpen}
        values={formApi || {}}
        onCancel={() => {
          setFormOpen(false);
          setFormApi(null);
        }}
        onSubmit={() => {
          setFormOpen(false);
          setFormApi(null);
          reloadApis();
          setPrincipalRefresh((n) => n + 1);
          resetHostIdentityCatalogCache();
        }}
      />
    </PageContainer>
  );
};

export default IdentityCatalogPage;

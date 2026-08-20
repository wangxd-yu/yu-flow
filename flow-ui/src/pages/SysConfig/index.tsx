import React, { useEffect, useMemo, useState } from 'react';
import {
  PageContainer,
  ProForm,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProCard,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { message, Tabs, Spin, Alert, Button, Tag } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import '@/styles/fullHeightTable.css';

const API_BASE = '/flow-api/sys-configs';

/**
 * 枚举配置约定（不改表结构）：
 * - valueType = ENUM
 * - remark 以方括号声明选项，其后为说明文案
 *   [VALUE|VALUE:展示名|...] 说明文字
 */
const ENUM_REMARK_RE = /^\[([^\]]+)]\s*([\s\S]*)$/;

export interface SysConfigDTO {
  id: string;
  configKey: string;
  configValue: string;
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON' | 'ENUM';
  configGroup: string;
  remark?: string;
  isBuiltin: 0 | 1;
  status: 0 | 1;
  sortOrder?: number;
}

type SelectOption = { label: string; value: string };

const parseEnumRemark = (
  remark?: string,
): { options: SelectOption[]; help: string } => {
  const raw = (remark || '').trim();
  const m = raw.match(ENUM_REMARK_RE);
  if (!m) {
    return { options: [], help: raw };
  }
  const options = m[1]
    .split('|')
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const idx = part.indexOf(':');
      if (idx <= 0) {
        return { value: part, label: part };
      }
      const value = part.slice(0, idx).trim();
      const labelTail = part.slice(idx + 1).trim();
      return {
        value,
        label: labelTail ? `${value} · ${labelTail}` : value,
      };
    })
    .filter((o) => o.value);
  return { options, help: (m[2] || '').trim() };
};

const displayRemark = (config: SysConfigDTO): string => {
  if ((config.valueType || '').toUpperCase() === 'ENUM') {
    return parseEnumRemark(config.remark).help;
  }
  return (config.remark || '').trim();
};

const GROUP_NAMES: Record<string, string> = {
  INGRESS: '入站防护',
  OPEN: '开放平台',
  SECURITY: '安全配置',
  PRIVACY: '出站隐私',
  GATEWAY: '网关配置',
  GENERAL: '通用配置',
  FLOW: '流程配置',
  ALERT: '运行告警',
  MAIL: '邮件 SMTP',
  LOG: '日志配置',
  OSS: '存储配置',
};

const GROUP_TAB_ORDER = [
  'INGRESS',
  'OPEN',
  'SECURITY',
  'PRIVACY',
  'GATEWAY',
  'GENERAL',
  'FLOW',
  'ALERT',
  'MAIL',
  'LOG',
  'OSS',
];

const GROUP_ALERTS: Record<string, { message: string; description: string }> = {
  INGRESS: {
    message: '入站防护 · 已发布业务 API',
    description:
      '优先级：本页 > application.yml；接口 securityConfig 可再覆盖（需发布）。authMode=NONE 仍受 allow-ingress-auth-none 约束。',
  },
  OPEN: {
    message: '开放平台 · AppKey 签名入口',
    description: '优先级：本页 > application.yml；entry-prefix 仅 yml、不热更。',
  },
  SECURITY: {
    message: '安全相关运行参数',
    description: '部分项仅 yml/环境变量生效，请以各项 ? 说明为准。',
  },
  PRIVACY: {
    message: '出站隐私 · 已发布 JSON',
    description:
      '优先级：本页 > application.yml。关闭传输封装后，命中明文规则的字段会以明文出现在 JSON 里，生产公网不建议关。修改后热更新，无需重启。',
  },
  GATEWAY: {
    message: '网关与路由相关',
    description: '与入站鉴权策略配合使用。',
  },
  ALERT: {
    message: '运行告警（Webhook）· 全局兜底',
    description: '无告警规则时生效；推荐在「运行观测 → 告警规则」配置通道与规则。',
  },
  MAIL: {
    message: '邮件 SMTP（全局）',
    description: '告警 Email 与流程发信共用；优先级：本页 > yu.flow.mail。',
  },
  LOG: {
    message: '日志定时清理',
    description: '保留天数 0=不清理；多节点靠 Redis 锁单点执行。',
  },
  FLOW: {
    message: '流程引擎相关',
    description: '修改后经缓存通道热更新。',
  },
  GENERAL: {
    message: '通用基础设施',
    description: '修改后通过 Redis Pub/Sub 热部署到各节点。',
  },
  OSS: {
    message: '对象存储',
    description: '密钥类字段保存后前端掩码展示，回填掩码不会覆盖原值。',
  },
};

const fetchAllConfigs = async (): Promise<SysConfigDTO[]> => {
  const result = await request(`${API_BASE}/page`, {
    method: 'GET',
    params: { page: 0, size: 1000 },
  });
  return result.items || [];
};

const updateSysConfig = async (id: string, data: any) => {
  return request(`${API_BASE}/${id}`, {
    method: 'PUT',
    data,
  });
};

const sortGroupKeys = (keys: string[]): string[] => {
  const rank = (g: string) => {
    const i = GROUP_TAB_ORDER.indexOf(g);
    return i >= 0 ? i : GROUP_TAB_ORDER.length + g.localeCompare('');
  };
  return [...keys].sort((a, b) => {
    const ra = rank(a);
    const rb = rank(b);
    if (ra !== rb) return ra - rb;
    return a.localeCompare(b);
  });
};

const SysConfigManage: React.FC = () => {
  const [loading, setLoading] = useState<boolean>(true);
  const [groupedData, setGroupedData] = useState<Record<string, SysConfigDTO[]>>({});
  const [activeTab, setActiveTab] = useState<string>('');

  const orderedGroups = useMemo(
    () => sortGroupKeys(Object.keys(groupedData)),
    [groupedData],
  );

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await fetchAllConfigs();
      const grouped: Record<string, SysConfigDTO[]> = {};
      data.forEach((item) => {
        const group = item.configGroup || 'GENERAL';
        if (!grouped[group]) {
          grouped[group] = [];
        }
        grouped[group].push(item);
      });
      Object.keys(grouped).forEach((g) => {
        grouped[g].sort((a, b) => {
          const sa = a.sortOrder ?? 100;
          const sb = b.sortOrder ?? 100;
          if (sa !== sb) return sa - sb;
          return (a.configKey || '').localeCompare(b.configKey || '');
        });
      });
      setGroupedData(grouped);
      const keys = sortGroupKeys(Object.keys(grouped));
      setActiveTab((prev) => (prev && grouped[prev] ? prev : keys[0] || prev));
    } catch (error) {
      message.error('加载系统配置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const parseValue = (config: SysConfigDTO) => {
    if (config.valueType === 'BOOLEAN') {
      return config.configValue === 'true' || config.configValue === '1';
    }
    if (config.valueType === 'NUMBER') {
      return Number(config.configValue);
    }
    return config.configValue;
  };

  const stringifyValue = (config: SysConfigDTO, val: any) => {
    if (config.valueType === 'BOOLEAN') {
      return val ? 'true' : 'false';
    }
    return String(val ?? '');
  };

  const handleSaveGroup = async (group: string, values: Record<string, any>) => {
    const configsInGroup = groupedData[group] || [];
    const changedConfigs = configsInGroup.filter((cfg) => {
      const formVal = values[cfg.configKey];
      return stringifyValue(cfg, formVal) !== (cfg.configValue || '');
    });

    if (changedConfigs.length === 0) {
      message.info('当前未修改任何配置');
      return true;
    }

    try {
      await Promise.all(
        changedConfigs.map((cfg) =>
          updateSysConfig(cfg.id, {
            ...cfg,
            configValue: stringifyValue(cfg, values[cfg.configKey]),
          }),
        ),
      );
      message.success('配置已保存生效');
      loadData();
      return true;
    } catch (error) {
      return false;
    }
  };

  const renderField = (config: SysConfigDTO) => {
    const allowEmpty = config.configKey === 'INGRESS_DEFAULT_IP_ALLOWLIST';
    const helpText = displayRemark(config);
    const valueType = (config.valueType || 'STRING').toUpperCase();
    // 不用 Form label 槽：antd 默认 label 高度 32px 会裁切多行说明
    const controlProps = {
      name: config.configKey,
      label: false as const,
      rules: allowEmpty ? [] : [{ required: true, message: `请输入 ${config.configKey}` }],
      formItemProps: { style: { marginBottom: 0 } },
    };

    let control: React.ReactNode | undefined;
    if (valueType === 'ENUM') {
      const { options } = parseEnumRemark(config.remark);
      if (options.length > 0) {
        control = (
          <ProFormSelect
            {...controlProps}
            width="md"
            options={options}
            fieldProps={{ allowClear: false, showSearch: false }}
          />
        );
      }
    }
    if (!control) {
      switch (valueType) {
        case 'BOOLEAN':
          control = <ProFormSwitch {...controlProps} />;
          break;
        case 'NUMBER':
          control = <ProFormDigit {...controlProps} width="sm" />;
          break;
        case 'JSON':
          control = (
            <ProFormTextArea
              {...controlProps}
              width="xl"
              fieldProps={{ rows: 4, style: { fontFamily: 'monospace' } }}
            />
          );
          break;
        default:
          if (config.configKey === 'MAIL_PASSWORD' || /PASSWORD|SECRET/i.test(config.configKey)) {
            control = (
              <ProFormText.Password
                {...controlProps}
                width="md"
                fieldProps={{ visibilityToggle: true }}
              />
            );
          } else {
            control = <ProFormText {...controlProps} width="md" />;
          }
      }
    }

    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 16,
          marginBottom: 16,
          paddingBottom: 12,
          borderBottom: '1px solid rgba(0,0,0,0.04)',
        }}
      >
        <div style={{ width: 420, flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            {!allowEmpty ? (
              <span style={{ color: '#ff4d4f', lineHeight: 1 }}>*</span>
            ) : (
              <span style={{ width: 7 }} />
            )}
            <span
              style={{
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                fontSize: 13,
                fontWeight: 600,
                color: 'rgba(0,0,0,0.88)',
              }}
            >
              {config.configKey}
            </span>
            <Tag style={{ margin: 0, lineHeight: '16px', fontSize: 11, padding: '0 4px' }}>
              {valueType}
            </Tag>
          </div>
          {helpText ? (
            <div
              style={{
                marginTop: 4,
                marginLeft: 13,
                fontSize: 12,
                lineHeight: 1.5,
                color: 'rgba(0,0,0,0.45)',
                whiteSpace: 'normal',
                wordBreak: 'break-word',
              }}
            >
              {helpText}
            </div>
          ) : null}
        </div>
        <div style={{ flex: 1, minWidth: 200, maxWidth: 420, paddingTop: 2 }}>{control}</div>
      </div>
    );
  };

  if (loading) {
    return (
      <PageContainer>
        <ProCard style={{ minHeight: '50vh', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
          <Spin size="large" tip="加载配置数据中..." />
        </ProCard>
      </PageContainer>
    );
  }

  const tabItems = orderedGroups.map((group) => {
    const configs = groupedData[group] || [];
    const initialValues = configs.reduce((acc, cfg) => {
      acc[cfg.configKey] = parseValue(cfg);
      return acc;
    }, {} as Record<string, any>);
    const alert = GROUP_ALERTS[group];

    return {
      key: group,
      label: GROUP_NAMES[group] || group,
      children: (
        <div style={{ maxWidth: 980, padding: '8px 8px 24px 16px' }}>
          {alert ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={alert.message}
              description={alert.description}
            />
          ) : null}

          <ProForm
            key={group}
            layout="horizontal"
            submitter={{
              render: (props) => (
                <div style={{ marginTop: 8 }}>
                  <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    onClick={() => props.form?.submit?.()}
                  >
                    保存配置
                  </Button>
                </div>
              ),
            }}
            initialValues={initialValues}
            onFinish={async (values) => handleSaveGroup(group, values)}
          >
            {configs.map((config) => (
              <React.Fragment key={config.id}>{renderField(config)}</React.Fragment>
            ))}
          </ProForm>
        </div>
      ),
    };
  });

  return (
    <PageContainer
      className="fh-container"
      style={{ height: 'calc(100vh - 26px)', overflow: 'hidden' }}
      header={{ title: '系统配置', subTitle: '热更新基础设施参数' }}
    >
      <Alert
        message="修改后经 Redis Pub/Sub 热更新至各节点，无需重启；请谨慎操作。"
        type="warning"
        showIcon
        closable
        style={{ marginBottom: 12, flexShrink: 0 }}
      />
      <ProCard
        style={{ flex: 1, minHeight: 0, overflow: 'auto' }}
        bodyStyle={{ height: '100%', paddingTop: 8 }}
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          tabPosition="left"
          items={tabItems}
          destroyInactiveTabPane={false}
        />
      </ProCard>
    </PageContainer>
  );
};

export default SysConfigManage;

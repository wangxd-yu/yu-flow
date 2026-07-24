import React, { useEffect, useState } from 'react';
import {
  PageContainer,
  ProForm,
  ProFormDigit,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProCard,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { message, Tabs, Spin, Alert, Typography, Button } from 'antd';
import { SaveOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;

const API_BASE = '/flow-api/sys-configs';

export interface SysConfigDTO {
  id: string;
  configKey: string;
  configValue: string;
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON';
  configGroup: string;
  remark?: string;
  isBuiltin: 0 | 1;
  status: 0 | 1;
  sortOrder?: number;
}

const GROUP_NAMES: Record<string, string> = {
  GENERAL: '通用配置',
  SECURITY: '安全配置',
  GATEWAY: '网关配置',
  OPEN: '开放平台',
  INGRESS: '入站防护',
  ALERT: '运行告警',
  MAIL: '邮件 SMTP',
  OSS: '存储配置',
  LOG: '日志配置',
  FLOW: '流程配置',
};

// 辅助方法：获取数据
const fetchAllConfigs = async (): Promise<SysConfigDTO[]> => {
  const result = await request(`${API_BASE}/page`, {
    method: 'GET',
    params: { page: 0, size: 1000 },
  });
  return result.items || [];
};

// 辅助方法：更新数据
const updateSysConfig = async (id: string, data: any) => {
  return request(`${API_BASE}/${id}`, {
    method: 'PUT',
    data,
  });
};

const SysConfigManage: React.FC = () => {
  const [loading, setLoading] = useState<boolean>(true);
  const [groupedData, setGroupedData] = useState<Record<string, SysConfigDTO[]>>({});
  const [activeTab, setActiveTab] = useState<string>('');

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await fetchAllConfigs();
      // 根据 configGroup 分组
      const grouped: Record<string, SysConfigDTO[]> = {};
      data.forEach((item) => {
        const group = item.configGroup || 'GENERAL';
        if (!grouped[group]) {
          grouped[group] = [];
        }
        grouped[group].push(item);
      });
      // 组内按 sortOrder / key 排序（后端已排序，此处兜底）
      Object.keys(grouped).forEach((g) => {
        grouped[g].sort((a, b) => {
          const sa = a.sortOrder ?? 100;
          const sb = b.sortOrder ?? 100;
          if (sa !== sb) return sa - sb;
          return (a.configKey || '').localeCompare(b.configKey || '');
        });
      });
      setGroupedData(grouped);
      setActiveTab((prev) =>
        prev && grouped[prev] ? prev : Object.keys(grouped)[0] || prev,
      );
    } catch (error) {
      message.error('加载系统配置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // 解析后端字符串值为前端表单需要的类型
  const parseValue = (config: SysConfigDTO) => {
    if (config.valueType === 'BOOLEAN') {
      return config.configValue === 'true' || config.configValue === '1';
    }
    if (config.valueType === 'NUMBER') {
      return Number(config.configValue);
    }
    return config.configValue;
  };

  // 序列化前端表单值为后端保存需要的字符串
  const stringifyValue = (config: SysConfigDTO, val: any) => {
    if (config.valueType === 'BOOLEAN') {
      return val ? 'true' : 'false';
    }
    return String(val ?? '');
  };

  const handleSaveGroup = async (group: string, values: Record<string, any>) => {
    const configsInGroup = groupedData[group] || [];
    
    // 找出该分组下被修改的配置项
    const changedConfigs = configsInGroup.filter((cfg) => {
      const formVal = values[cfg.configKey];
      const newStrVal = stringifyValue(cfg, formVal);
      return newStrVal !== (cfg.configValue || '');
    });

    if (changedConfigs.length === 0) {
      message.info('当前未修改任何配置');
      return true;
    }

    try {
      // 通过 Promise.all 批量发送多次更新请求
      await Promise.all(
        changedConfigs.map((cfg) => {
          const formVal = values[cfg.configKey];
          return updateSysConfig(cfg.id, {
            ...cfg,
            configValue: stringifyValue(cfg, formVal),
          });
        }),
      );
      message.success('配置已保存生效');
      loadData(); // 重新加载以更新本地缓存原始值
      return true;
    } catch (error) {
      // 错误由全局拦截器处理
      return false;
    }
  };

  const renderField = (config: SysConfigDTO) => {
    // 允许空串的配置（如 IP 白名单「不限制」）
    const allowEmpty = config.configKey === 'INGRESS_DEFAULT_IP_ALLOWLIST';
    const commonProps = {
      name: config.configKey,
      label: config.configKey,
      tooltip: config.isBuiltin === 1 ? '系统内置核心参数' : '用户自定义参数',
      extra: config.remark,
      rules: allowEmpty ? [] : [{ required: true, message: `请输入 ${config.configKey}` }],
      width: 'md' as const,
      formItemProps: { style: { marginBottom: 12 } },
    };

    switch (config.valueType) {
      case 'BOOLEAN':
        return <ProFormSwitch {...commonProps} />;
      case 'NUMBER':
        return <ProFormDigit {...commonProps} />;
      case 'JSON':
        return (
          <ProFormTextArea
            {...commonProps}
            fieldProps={{ rows: 6 }}
            style={{ fontFamily: 'monospace' }}
          />
        );
      case 'STRING':
      default:
        if (config.configKey === 'MAIL_PASSWORD' || /PASSWORD|SECRET/i.test(config.configKey)) {
          return (
            <ProFormText.Password
              {...commonProps}
              fieldProps={{ visibilityToggle: true }}
            />
          );
        }
        return <ProFormText {...commonProps} />;
    }
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

  const tabItems = Object.entries(groupedData).map(([group, configs]) => {
    // 组装 initialValues
    const initialValues = configs.reduce((acc, cfg) => {
      acc[cfg.configKey] = parseValue(cfg);
      return acc;
    }, {} as Record<string, any>);

    return {
      key: group,
      label: GROUP_NAMES[group] || group,
      children: (
        <div style={{ maxWidth: 800, padding: '24px 0' }}>
          <Title level={4} style={{ marginBottom: 8 }}>{GROUP_NAMES[group] || group}</Title>
          {group === 'INGRESS' ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="优先级：系统配置 > application.yml"
              description="本页启用的 INGRESS_* 会覆盖 yu.flow.ingress；停用或删除配置项后自动回退 yml。接口 securityConfig 可再覆盖鉴权/限流/超时等，需发布后生效。"
            />
          ) : group === 'OPEN' ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="优先级：系统配置 > application.yml"
              description="本页启用的 OPEN_* 会覆盖 yu.flow.open；entry-prefix 仅 yml 配置、不热更。"
            />
          ) : group === 'ALERT' ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="运行告警（Webhook）· 全局兜底"
              description="无启用「告警规则」时，本页配置生效：按间隔扫描近窗口异常 TopN 并 POST 到 Webhook。推荐在「运行观测 → 告警规则」配置通道与规则；同一资产在静默期内不会重复推送。"
            />
          ) : group === 'MAIL' ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="邮件 SMTP（全局）"
              description="告警 Email 通道与后续流程编排发信节点共用此 SMTP。优先级：系统配置 > application.yml（yu.flow.mail）。配置后可在告警通道里点「测试」验证。"
            />
          ) : group === 'LOG' ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="日志定时清理"
              description="服务启动约 1 分钟后首轮清理，之后每 24 小时执行一次。各 LOG_*_RETENTION_DAYS 为对应表保留天数；设为 0 表示不清理该类日志。多节点部署时通过 Redis 锁保证仅一节点执行。"
            />
          ) : (
            <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
              该面板展示属于【{GROUP_NAMES[group] || group}】类别的所有底层基础设施配置。
            </Text>
          )}
          
          <ProForm
            initialValues={initialValues}
            onFinish={async (values) => handleSaveGroup(group, values)}
            submitter={{
              render: (props, doms) => {
                return (
                  <div style={{ marginTop: 24 }}>
                    <Button
                      type="primary"
                      key="submit"
                      icon={<SaveOutlined />}
                      onClick={() => props.form?.submit?.()}
                    >
                      保存配置
                    </Button>
                  </div>
                );
              },
            }}
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
    <PageContainer>
      <Alert
        message="基础设施参数设置"
        description="此处参数修改后将通过底层 L2 缓存通道 (Redis Pub/Sub) 实时热部署广播至所有集群节点内存，无须重启服务即可生效。请谨慎操作。"
        type="warning"
        showIcon
        closable
        style={{ marginBottom: 24 }}
      />
      <ProCard>
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

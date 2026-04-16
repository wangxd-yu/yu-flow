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
}

const GROUP_NAMES: Record<string, string> = {
  GENERAL: '通用配置',
  SECURITY: '安全配置',
  GATEWAY: '网关配置',
  OSS: '存储配置',
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
      setGroupedData(grouped);
      if (Object.keys(grouped).length > 0) {
        setActiveTab(Object.keys(grouped)[0]);
      }
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

    const hide = message.loading(`正在保存 [${GROUP_NAMES[group] || group}] 的配置...`);
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
      hide();
      message.success('配置已保存生效');
      loadData(); // 重新加载以更新本地缓存原始值
      return true;
    } catch (error) {
      hide();
      message.error('保存失败，请检查网络');
      return false;
    }
  };

  const renderField = (config: SysConfigDTO) => {
    const commonProps = {
      name: config.configKey,
      label: config.configKey,
      tooltip: config.isBuiltin === 1 ? '系统内置核心参数' : '用户自定义参数',
      extra: config.remark,
      rules: [{ required: true, message: `请输入 ${config.configKey}` }],
      width: 'md' as const,
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
          <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
            该面板展示属于【{GROUP_NAMES[group] || group}】类别的所有底层基础设施配置。
          </Text>
          
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
              <div key={config.id} style={{ marginBottom: 24 }}>
                {renderField(config)}
              </div>
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

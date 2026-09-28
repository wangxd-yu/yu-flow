import { Space, Tag, Tooltip } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { getCurrentEnv, type CurrentEnv } from '@/services/flow/releaseService';

const ENV_COLORS: Record<string, string> = {
  PROD: 'red',
  STAGING: 'orange',
  TEST: 'orange',
  DEV: 'blue',
};

/**
 * 顶栏环境标识。仅在部署时配置了 current-env 或锁定了编辑时显示，避免未启用多环境的部署多出一个 DEV 标签。
 */
export default function CurrentEnvBadge() {
  const [env, setEnv] = useState<CurrentEnv | null>(null);

  useEffect(() => {
    getCurrentEnv().then(setEnv);
  }, []);

  if (!env?.locked && !env?.editLocked) return null;

  return (
    <Space size={4}>
      {env.locked && (
        <Tooltip title="本实例所属环境，发布门禁与回归均按此环境判定">
          <Tag color={ENV_COLORS[env.code] || 'default'} style={{ marginInlineEnd: 0, fontWeight: 600 }}>
            {env.name}（{env.code}）
          </Tag>
        </Tooltip>
      )}
      {env.editLocked && (
        <Tooltip title="本环境已锁定资产编辑，接口 / 服务 / 任务等变更只能通过发布包导入">
          <Tag icon={<LockOutlined />} style={{ marginInlineEnd: 0 }}>
            编辑已锁定
          </Tag>
        </Tooltip>
      )}
    </Space>
  );
}

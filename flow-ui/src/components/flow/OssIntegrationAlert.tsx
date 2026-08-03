import { Alert } from 'antd';
import React, { useEffect, useState } from 'react';
import { getOssIntegrationStatus } from '@/services/flow/ossIntegration';

const OssIntegrationAlert: React.FC = () => {
  const [hints, setHints] = useState<string[]>([]);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const status = await getOssIntegrationStatus();
        if (cancelled) return;
        const builtin =
          status.principalProvider === 'BUILTIN_JWT' ||
          status.dataScopeProvider === 'BUILTIN_JWT';
        if (builtin && status.hints?.length) {
          setHints(status.hints);
          setVisible(true);
        }
      } catch {
        /* 忽略：不影响页面主流程 */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (!visible || !hints.length) {
    return null;
  }

  return (
    <Alert
      type="warning"
      showIcon
      style={{ marginBottom: 16 }}
      message="对象存储集成提示"
      description={
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          {hints.map((hint) => (
            <li key={hint}>{hint}</li>
          ))}
        </ul>
      }
    />
  );
};

export default OssIntegrationAlert;

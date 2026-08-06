import { Alert, Typography } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link } from '@umijs/max';
import { getOssIntegrationStatus } from '@/services/flow/ossIntegration';

const { Text } = Typography;

const OssIntegrationAlert: React.FC = () => {
  const [hints, setHints] = useState<string[]>([]);
  const [visible, setVisible] = useState(false);
  const [isBuiltin, setIsBuiltin] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const status = await getOssIntegrationStatus();
        if (cancelled) return;
        const builtin =
          status.principalProvider === 'BUILTIN_JWT' ||
          status.dataScopeProvider === 'BUILTIN_JWT';
        
        setIsBuiltin(builtin);
        if (status.hints?.length) {
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
      type={isBuiltin ? 'warning' : 'success'}
      showIcon
      style={{ marginBottom: 16 }}
      message={
        <span style={{ fontWeight: 600 }}>
          {isBuiltin ? '系统集成与安全建议 (独立运行模式)' : '系统集成状态 (已集成)'}
        </span>
      }
      description={
        <div style={{ marginTop: 8 }}>
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            {hints.map((hint, i) => (
              <li key={i} style={{ marginBottom: 6, color: '#555' }} dangerouslySetInnerHTML={{ __html: hint }} />
            ))}
          </ul>
          {isBuiltin && (
            <div style={{ marginTop: 12 }}>
              <Link to="/integration/docs?doc=auth-spi" style={{ fontWeight: 500 }}>
                👉 点击查看详细《宿主系统对接与安全认证指南》
              </Link>
            </div>
          )}
        </div>
      }
    />
  );
};

export default OssIntegrationAlert;

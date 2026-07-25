import React, { useState } from 'react';
import { Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import ChangePasswordModal from '@/components/ChangePasswordModal';
import { logoutRemote } from '@/services/auth';

type Props = {
  displayName: string;
  legacyAdmin?: boolean;
};

/**
 * 顶栏用户入口：帮助文档 / 修改密码 / 退出登录
 */
const UserHeaderActions: React.FC<Props> = ({ displayName }) => {
  const [pwdOpen, setPwdOpen] = useState(false);

  const onLogout = async () => {
    await logoutRemote();
    history.push('/login');
  };

  const flatItems: MenuProps['items'] = [
    {
      key: 'help-docs',
      label: '帮助文档',
      icon: <QuestionCircleOutlined />,
    },
    { type: 'divider' },
    {
      key: 'change-password',
      label: '修改密码',
    },
    { type: 'divider' },
    {
      key: 'logout',
      label: '退出登录',
      danger: true,
    },
  ];

  const onClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'help-docs') {
      window.open('https://github.com/wangxd-yu/yu-flow#readme', '_blank', 'noopener,noreferrer');
      return;
    }
    if (key === 'change-password') {
      setPwdOpen(true);
      return;
    }
    if (key === 'logout') {
      onLogout();
    }
  };

  return (
    <>
      <Dropdown
        menu={{ items: flatItems, onClick }}
        placement="bottomRight"
        trigger={['click']}
      >
        <button
          type="button"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            height: 32,
            padding: '0 10px 0 4px',
            marginRight: 4,
            border: '1px solid transparent',
            borderRadius: 20,
            background: 'transparent',
            cursor: 'pointer',
            font: 'inherit',
            color: 'inherit',
            transition: 'background 0.2s, border-color 0.2s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(0,0,0,0.04)';
            e.currentTarget.style.borderColor = 'rgba(0,0,0,0.06)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'transparent';
            e.currentTarget.style.borderColor = 'transparent';
          }}
          title={displayName}
        >
          <span
            style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
              color: '#fff',
              fontSize: 13,
              fontWeight: 600,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            {(displayName || 'U').slice(0, 1).toUpperCase()}
          </span>
          <span style={{ fontSize: 13, maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {displayName || '用户'}
          </span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ opacity: 0.55 }}>
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>
      </Dropdown>
      <ChangePasswordModal open={pwdOpen} onOpenChange={setPwdOpen} />
    </>
  );
};

export default UserHeaderActions;

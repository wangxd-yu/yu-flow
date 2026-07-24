import React from 'react';
import { Drawer } from 'antd';
import './AssetFormShell.less';

export const ASSET_FORM_SHELL_CLASS = 'yf-asset-form-shell';
export const ASSET_FORM_FILL_CLASS = 'yf-asset-form-fill';
export const ASSET_FORM_SHELL_PLAIN_CLASS = 'yf-asset-form-shell-plain';

export type AssetFormShellProps = {
  open: boolean;
  onClose: () => void;
  destroyOnClose?: boolean;
  /** Drawer body 内主内容（通常为 PageContainer + 附属抽屉） */
  children: React.ReactNode;
  className?: string;
};

/**
 * 全屏资产表单 Drawer 外壳：统一 padding / tabs / PageContainer 填高样式。
 * 业务 Header、TabList、发布按钮等由 children 自行组合。
 */
const AssetFormShell: React.FC<AssetFormShellProps> = ({
  open,
  onClose,
  destroyOnClose = true,
  children,
  className,
}) => (
  <Drawer
    title={null}
    width="100%"
    open={open}
    onClose={onClose}
    closable={false}
    className={className}
    styles={{
      body: {
        padding: 0,
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
      },
    }}
    destroyOnClose={destroyOnClose}
  >
    {children}
  </Drawer>
);

export default AssetFormShell;

import React from 'react';
import { Drawer } from 'antd';
import './AssetFormShell.less';

export const ASSET_FORM_SHELL_CLASS = 'yf-asset-form-shell';
export const ASSET_FORM_FILL_CLASS = 'yf-asset-form-fill';
/** 可滚动 Tab 内容（基本信息等） */
export const ASSET_FORM_SCROLL_CLASS = 'yf-asset-form-scroll';
/** 基本信息表单内容宽度约束 */
export const ASSET_FORM_BASIC_CLASS = 'yf-asset-form-basic';
export const ASSET_FORM_SHELL_PLAIN_CLASS = 'yf-asset-form-shell-plain';

/**
 * 基本信息响应式列宽：&lt;md 1 列，md~xl 2 列，≥xl 3 列。
 * 整行字段用 {@link ASSET_FORM_COL_FULL}。
 */
export const ASSET_FORM_COL_FIELD = { xs: 24, md: 12, xl: 8 } as const;
/** 中等宽字段：最多 2 列 */
export const ASSET_FORM_COL_HALF = { xs: 24, md: 12, xl: 12 } as const;
/** 占满整行（策略按钮组、多行描述等） */
export const ASSET_FORM_COL_FULL = { xs: 24 } as const;

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

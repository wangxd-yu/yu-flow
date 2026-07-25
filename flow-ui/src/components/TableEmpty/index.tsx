/**
 * 列表统一空态：区分「真没有数据」与「筛选无结果」，
 * 前者给新建 CTA 引导，后者提示调整筛选条件。
 *
 * 用法（ProTable / Table）：
 *   locale={{ emptyText: <TableEmpty entityName="接口" onCreate={...} /> }}
 */
import React from 'react';
import { Button, Empty, Space, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';

export interface TableEmptyProps {
  /** 资产名称，如「接口」「任务」「服务」 */
  entityName: string;
  /** 主 CTA：新建；不传则只展示文案 */
  onCreate?: () => void;
  /** CTA 按钮文案，默认「新建{entityName}」 */
  createText?: string;
  /** 次级操作（如「从宿主导入」「从 cURL 导入」） */
  extraActions?: React.ReactNode;
  /** 处于目录/关键字筛选中：文案切换为「无匹配结果」，不显示新建 CTA */
  filtered?: boolean;
  /** 补充说明文案 */
  hint?: string;
}

const TableEmpty: React.FC<TableEmptyProps> = ({
  entityName,
  onCreate,
  createText,
  extraActions,
  filtered = false,
  hint,
}) => {
  if (filtered) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        style={{ padding: '32px 0' }}
        description={
          <Typography.Text type="secondary">
            当前筛选条件下没有{entityName}，试试切换目录或清空搜索条件
          </Typography.Text>
        }
      />
    );
  }

  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      style={{ padding: '32px 0' }}
      description={
        <Space direction="vertical" size={4}>
          <Typography.Text>还没有{entityName}</Typography.Text>
          {hint ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {hint}
            </Typography.Text>
          ) : null}
        </Space>
      }
    >
      {(onCreate || extraActions) && (
        <Space>
          {onCreate && (
            <Button type="primary" icon={<PlusOutlined />} onClick={onCreate}>
              {createText || `新建${entityName}`}
            </Button>
          )}
          {extraActions}
        </Space>
      )}
    </Empty>
  );
};

export default TableEmpty;

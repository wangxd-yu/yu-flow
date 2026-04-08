import React, { useMemo } from 'react';
import { ProFormTreeSelect } from '@ant-design/pro-components';
import { request } from '@umijs/max';

async function getDirectoryTree() {
  const res = await request<any>('/flow-api/directories/tree', { method: 'GET' });
  return Array.isArray(res) ? res : res?.data ?? [];
}

/** 格式化树结构，适配 TreeSelect */
const formatTreeData = (data: any[]): any[] => {
  return data.map((item) => ({
    title: item.name,
    value: item.id,
    key: item.id,
    children: item.children ? formatTreeData(item.children) : [],
  }));
};

export const DirectoryTreeSelect: React.FC<any> = (props) => {
  return (
    <ProFormTreeSelect
      name="targetDirectoryId"
      label="目标目录"
      placeholder="请选择要移动到的目标目录（不选默认为根目录）"
      allowClear
      rules={[{ required: false }]}
      request={async () => {
        const data = await getDirectoryTree();
        return formatTreeData(data);
      }}
      fieldProps={{
        showSearch: true,
        treeDefaultExpandAll: true,
      }}
      {...props}
    />
  );
};

export default DirectoryTreeSelect;

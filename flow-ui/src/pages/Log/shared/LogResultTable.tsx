import React from 'react';
import { Empty, Table, Typography } from 'antd';

const { Text } = Typography;

/** 从常见包装结构中提取行数组与分页信息 */
const extractRows = (data: any): {
  rows: any[];
  pageInfo: { total?: number; page?: number; totalPage?: number } | null;
} => {
  if (!data) return { rows: [], pageInfo: null };

  if (Array.isArray(data)) {
    return { rows: data, pageInfo: null };
  }

  if (typeof data !== 'object') {
    return { rows: [], pageInfo: null };
  }

  const listKeys = ['items', 'content', 'rows', 'list', 'records', 'result'];
  for (const key of listKeys) {
    if (Array.isArray(data[key])) {
      return {
        rows: data[key],
        pageInfo: {
          total: data.total ?? data.totalElements ?? data.count,
          page: data.page ?? data.pageNum ?? data.number,
          totalPage: data.totalPage ?? data.totalPages,
        },
      };
    }
  }

  // 常见包装：{ code, data: T } / { success, data: T }
  if (data.data !== undefined) {
    const inner = data.data;
    if (Array.isArray(inner)) {
      return {
        rows: inner,
        pageInfo: {
          total: data.total ?? inner.length,
          page: data.page,
          totalPage: data.totalPage,
        },
      };
    }
    if (inner && typeof inner === 'object') {
      for (const key of listKeys) {
        if (Array.isArray(inner[key])) {
          return {
            rows: inner[key],
            pageInfo: {
              total: inner.total ?? inner.totalElements ?? data.total ?? inner.count,
              page: inner.page ?? inner.pageNum ?? data.page,
              totalPage: inner.totalPage ?? inner.totalPages ?? data.totalPage,
            },
          };
        }
      }
      // 单个业务对象：{ code, data: { id, name, ... } }
      if (!Array.isArray(inner) && Object.keys(inner).length > 0) {
        return { rows: [inner], pageInfo: null };
      }
    }
  }

  // 顶层单个对象（非列表包装）也允许一行展示
  return { rows: [data], pageInfo: null };
};

/** 判断响应是否适合表格化展示 */
export const isTabularData = (data: any): boolean => {
  if (data == null) return false;
  const { rows } = extractRows(data);
  if (rows.length === 0) return false;
  // 至少有一行对象
  return typeof rows[0] === 'object' && rows[0] !== null && !Array.isArray(rows[0]);
};

/**
 * 将多种 List / Page / 包装结构渲染为 Ant Table。
 * 支持：
 * - 数组: [...]
 * - 分页: { items|list|rows|content|records, total }
 * - 业务包装: { code, data: [...] } / { data: { list, total } }
 * - 单对象: { id, name, ... } 或 { data: { id, name } }
 */
const LogResultTable: React.FC<{ data: any }> = ({ data }) => {
  const { rows, pageInfo } = extractRows(data);

  if (rows.length === 0) {
    return <Empty description="查询结果为空" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  if (typeof rows[0] !== 'object' || rows[0] === null || Array.isArray(rows[0])) {
    return <Empty description="响应不是表格结构，请切换「原始 JSON」查看" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  // 合并前几行的 key，兼容稀疏字段
  const keySet = new Set<string>();
  rows.slice(0, 20).forEach((row) => {
    if (row && typeof row === 'object') {
      Object.keys(row).forEach((k) => keySet.add(k));
    }
  });
  const keys = Array.from(keySet);
  if (keys.length === 0) {
    return <Empty description="无可展示字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  const columns = keys.map((key) => ({
    title: key,
    dataIndex: key,
    key,
    ellipsis: true,
    render: (val: any) => {
      if (val === null || val === undefined || val === '') {
        return <Text type="secondary">-</Text>;
      }
      if (typeof val === 'object') {
        return <Text code style={{ fontSize: 11 }}>{JSON.stringify(val)}</Text>;
      }
      return String(val);
    },
  }));

  return (
    <div className="log-result-table-wrap">
      {pageInfo?.total != null && (
        <div className="log-result-table-meta">
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 <Text strong>{pageInfo.total}</Text> 条记录
            {pageInfo.totalPage != null && <>，共 {pageInfo.totalPage} 页</>}
          </Text>
        </div>
      )}
      <div className="log-result-table-body">
        <Table
          columns={columns}
          dataSource={rows.map((r, i) => ({ ...r, __key: i }))}
          rowKey="__key"
          size="small"
          bordered
          scroll={{ x: 'max-content' }}
          pagination={rows.length > 50 ? { pageSize: 50, showSizeChanger: true, size: 'small' } : false}
          style={{ fontSize: 12 }}
        />
      </div>
    </div>
  );
};

export default React.memo(LogResultTable);

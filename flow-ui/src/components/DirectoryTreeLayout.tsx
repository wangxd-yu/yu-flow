import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { ProCard } from '@ant-design/pro-components';
import {
  Button,
  Input,
  message,
  Space,
  Tree,
  Tooltip,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FolderOutlined,
  FolderOpenOutlined,
  FileOutlined,
} from '@ant-design/icons';
import { request } from '@umijs/max';
import type { DataNode, TreeProps } from 'antd/es/tree';

// ================================================================
// 全局目录 API — 统一接口
// ================================================================

async function getDirectoryTree() {
  return request<any[]>('/flow-api/directories/tree', { method: 'GET' });
}

async function addDirectory(data: { parentId?: string; name: string; sort?: number }) {
  return request('/flow-api/directories', { method: 'POST', data });
}

async function updateDirectory(id: string, data: { name?: string; sort?: number }) {
  return request(`/flow-api/directories/${id}`, { method: 'PUT', data });
}

async function deleteDirectory(id: string) {
  return request(`/flow-api/directories/${id}`, { method: 'DELETE' });
}

// ================================================================
// 后端目录 DTO → antd DataNode 转换
// ================================================================
function convertToTreeData(dirs: any[]): DataNode[] {
  return dirs.map((dir) => ({
    title: dir.name,
    key: dir.id,
    children: dir.children?.length ? convertToTreeData(dir.children) : [],
  }));
}

/** 递归收集所有节点 key（用于默认全部展开） */
function collectAllKeys(dirs: any[]): string[] {
  const keys: string[] = [];
  const walk = (nodes: any[]) => {
    nodes.forEach((n) => {
      keys.push(n.id ?? n.key);
      if (n.children?.length) walk(n.children);
    });
  };
  walk(dirs);
  return keys;
}

// ================================================================
// 目录树节点 — 悬浮操作按钮
// ================================================================
const TreeNodeTitle: React.FC<{
  nodeData: DataNode;
  onAdd?: (key: string) => void;
  onRename?: (key: string) => void;
  onDelete?: (key: string) => void;
}> = ({ nodeData, onAdd, onRename, onDelete }) => {
  const isRoot = nodeData.key === 'root';

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        width: '100%',
        paddingRight: 4,
      }}
      className="tree-node-title"
    >
      <Typography.Text
        ellipsis={{ tooltip: nodeData.title as string }}
        style={{ flex: 1, minWidth: 0 }}
      >
        {nodeData.title as string}
      </Typography.Text>

      <Space
        size={2}
        className="tree-node-actions"
        style={{ marginLeft: 8, flexShrink: 0 }}
      >
        {!nodeData.isLeaf && (
          <Tooltip title="新建子目录" mouseEnterDelay={0.5}>
            <Button
              type="text"
              size="small"
              icon={<PlusOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                onAdd?.(nodeData.key as string);
              }}
              style={{ width: 20, height: 20, fontSize: 12 }}
            />
          </Tooltip>
        )}
        {!isRoot && (
          <>
            <Tooltip title="重命名" mouseEnterDelay={0.5}>
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  onRename?.(nodeData.key as string);
                }}
                style={{ width: 20, height: 20, fontSize: 12 }}
              />
            </Tooltip>
            <Tooltip title="删除" mouseEnterDelay={0.5}>
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete?.(nodeData.key as string);
                }}
                style={{ width: 20, height: 20, fontSize: 12 }}
              />
            </Tooltip>
          </>
        )}
      </Space>
    </div>
  );
};

// ================================================================
// 全局 CSS（注入一次）
// ================================================================
const treeStyles = `
  .dir-tree-layout .tree-node-actions {
    opacity: 0;
    transition: opacity 0.2s;
  }
  .dir-tree-layout .ant-tree-node-content-wrapper:hover .tree-node-actions {
    opacity: 1;
  }
  .dir-tree-layout .directory-tree .ant-tree-node-content-wrapper {
    display: flex;
    align-items: center;
    width: calc(100% - 24px);
  }
  .dir-tree-layout .directory-tree .ant-tree-node-content-wrapper .ant-tree-title {
    flex: 1;
    min-width: 0;
  }
`;

// ================================================================
// Props 契约
// ================================================================
export interface DirectoryTreeLayoutProps {
  /** render-props：将选中的 directoryId 传递给子组件 */
  children: (selectedDirectoryId: string | undefined, selectedDirectoryName?: string) => React.ReactNode;
  /** 左侧目录树面板宽度，默认 250px */
  treeWidth?: string | number;
  /** 容器高度，默认 100% */
  height?: string | number;
}

// ================================================================
// <DirectoryTreeLayout /> 主组件
// ================================================================
const DirectoryTreeLayout: React.FC<DirectoryTreeLayoutProps> = ({
  children,
  treeWidth = '250px',
  height = '100%',
}) => {
  // ---- 目录树状态 ----
  const [selectedDirKey, setSelectedDirKey] = useState<string>();
  const [treeSearchValue, setTreeSearchValue] = useState('');
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [treeData, setTreeData] = useState<DataNode[]>([]);

  // ---- 加载目录树 ----
  const loadTree = useCallback(async () => {
    try {
      const res = await getDirectoryTree();
      const anyRes = res as any;
      const data = Array.isArray(anyRes) ? anyRes : anyRes?.data ?? [];
      setTreeData(convertToTreeData(data));
      // 默认展开所有节点
      setExpandedKeys(collectAllKeys(data));
    } catch (err) {
      console.error('获取目录树失败', err);
    }
  }, []);

  useEffect(() => {
    loadTree();
  }, [loadTree]);

  // ---- 搜索过滤 ----
  const filteredTreeData = useMemo(() => {
    if (!treeSearchValue) return treeData;

    const filterNodes = (nodes: DataNode[]): DataNode[] => {
      return nodes
        .map((node) => {
          const title = node.title as string;
          const childNodes = node.children ? filterNodes(node.children) : [];
          if (title.includes(treeSearchValue) || childNodes.length > 0) {
            return { ...node, children: childNodes };
          }
          return null;
        })
        .filter(Boolean) as DataNode[];
    };
    return filterNodes(treeData);
  }, [treeData, treeSearchValue]);

  // ---- 树节点选择 ----
  const [selectedDirName, setSelectedDirName] = useState<string>();

  const handleTreeSelect: TreeProps['onSelect'] = (selectedKeys, info) => {
    const key = selectedKeys[0] as string;
    setSelectedDirKey(key || undefined);
    setSelectedDirName(key ? (info.node.title as string) : undefined);
  };

  // ---- 目录 CRUD ----
  const handleAddDir = async (parentKey: string) => {
    const name = window.prompt('请输入子目录名称');
    if (!name) return;
    try {
      await addDirectory({ parentId: parentKey, name });
      message.success('目录创建成功');
      loadTree();
    } catch {
      message.error('创建失败');
    }
  };

  const handleRenameDir = async (key: string) => {
    const name = window.prompt('请输入新的目录名称');
    if (!name) return;
    try {
      await updateDirectory(key, { name });
      message.success('重命名成功');
      loadTree();
    } catch {
      message.error('重命名失败');
    }
  };

  const handleDeleteDir = async (key: string) => {
    try {
      await deleteDirectory(key);
      message.success('目录已删除');
      // 如果删除的正是当前选中目录，则清空选中
      if (selectedDirKey === key) {
        setSelectedDirKey(undefined);
      }
      loadTree();
    } catch (err: any) {
      message.error(err?.message || '删除失败，该目录下可能还有子目录或关联资产');
    }
  };

  // ---- 渲染 ----
  return (
    <div className="dir-tree-layout" style={{ height }}>
      <style>{treeStyles}</style>

      <ProCard split="vertical" bordered style={{ height: '100%' }}>
        {/* ========== 左侧：全局目录树 ========== */}
        <ProCard
          colSpan={treeWidth}
          style={{ height: '100%' }}
          bodyStyle={{
            paddingInline: 8,
            paddingBlock: 12,
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <Input.Search
            placeholder="搜索目录"
            allowClear
            size="small"
            style={{ marginBottom: 12 }}
            onChange={(e) => setTreeSearchValue(e.target.value)}
          />

          <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden' }}>
          <Tree
            className="directory-tree"
            treeData={filteredTreeData}
            selectedKeys={selectedDirKey ? [selectedDirKey] : []}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys)}
            onSelect={handleTreeSelect}
            blockNode
            showIcon
            icon={(props: any) => {
              if (props.data?.isLeaf) return <FileOutlined />;
              return props.expanded ? <FolderOpenOutlined /> : <FolderOutlined />;
            }}
            titleRender={(nodeData) => (
              <TreeNodeTitle
                nodeData={nodeData}
                onAdd={handleAddDir}
                onRename={handleRenameDir}
                onDelete={handleDeleteDir}
              />
            )}
          />
          </div>
        </ProCard>

        {/* ========== 右侧：业务内容渲染区 ========== */}
        <ProCard bodyStyle={{ paddingInline: 0 }}>
          {children(selectedDirKey, selectedDirName)}
        </ProCard>
      </ProCard>
    </div>
  );
};

export default DirectoryTreeLayout;

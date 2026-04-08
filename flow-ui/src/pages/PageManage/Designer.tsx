import React, { useState, useEffect, useRef, useCallback, Suspense } from 'react';
import { useParams, history } from '@umijs/max';
import { Button, Switch, message, Spin, Space } from 'antd';
import { ArrowLeftOutlined, SaveOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons';

// amis 运行时样式（编辑器组件自身会在懒加载时引入 editor-core 样式）
import 'amis/lib/themes/cxd.css';
import 'amis/lib/helper.css';
import 'amis-editor-core/lib/style.css';

// ============ 懒加载 Amis Editor（代码分割，不打入主包） ============
const LazyEditor = React.lazy(() =>
  import('amis-editor').then((module) => ({ default: module.Editor }))
);

// 懒加载 CRUD3 插件（跟随编辑器一起拆分）
const loadPlugins = () => {
  import('./plugin/CRUD3/CRUDTable');
  import('./plugin/CRUD3/CRUDList');
  import('./plugin/CRUD3/CRUDCards');
};

import { getPageDetail, updatePageJson } from './services/pageManage';
import { createAmisEnv } from '@/utils/amisEnv';

/** 默认页面骨架（新页面使用） */
const DEFAULT_JSON = {
  type: 'page',
  title: '新页面',
  body: [],
};

export interface DesignerProps {
  id?: string;
  onBack?: () => void;
}

// ============ Header 子组件：隔离高频编辑器变更的重渲染 ============
const DesignerHeader = React.memo<{
  pageName: string;
  isPreview: boolean;
  saving: boolean;
  onBack?: () => void;
  onTogglePreview: (checked: boolean) => void;
  onSave: () => void;
}>(({ pageName, isPreview, saving, onBack, onTogglePreview, onSave }) => (
  <div
    style={{
      height: 50,
      minHeight: 50,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 16px',
      borderBottom: '1px solid #e8e8e8',
      background: '#fff',
      zIndex: 10,
    }}
  >
    {/* 左侧：返回 + 页面名称 */}
    <Space size="middle">
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => {
          if (onBack) {
            onBack();
          } else {
            history.push('/page-manage/list');
          }
        }}
      >
        返回
      </Button>
      <span
        style={{
          fontSize: 16,
          fontWeight: 600,
          color: '#1d2129',
        }}
      >
        {pageName}
      </span>
    </Space>

    {/* 右侧：预览开关 + 保存 */}
    <Space size="middle">
      <Space size={6}>
        {isPreview ? (
          <EyeOutlined style={{ color: '#1677ff' }} />
        ) : (
          <EditOutlined />
        )}
        <Switch
          checkedChildren="预览"
          unCheckedChildren="编辑"
          checked={isPreview}
          onChange={onTogglePreview}
        />
      </Space>
      <Button
        type="primary"
        icon={<SaveOutlined />}
        onClick={onSave}
        loading={saving}
      >
        保存
      </Button>
    </Space>
  </div>
));

const Designer: React.FC<DesignerProps> = ({ id: propId, onBack }) => {
  const params = useParams<{ id: string }>();
  const id = propId || params.id;

  // 使用 useRef 追踪最新的 schema，避免高频 onChange 触发整棵组件树重渲染
  const jsonRef = useRef<any>(DEFAULT_JSON);
  const [isPreview, setIsPreview] = useState(false);
  const [loading, setLoading] = useState(true);
  const [pageName, setPageName] = useState('');
  const [saving, setSaving] = useState(false);

  // 用于传递给 Editor 的 value state（仅在初始加载和后端数据写入时触发更新）
  const [editorValue, setEditorValue] = useState<any>(DEFAULT_JSON);

  const env = React.useMemo(() => createAmisEnv(), []);

  // 触发 CRUD3 插件懒加载
  useEffect(() => {
    loadPlugins();
  }, []);

  // 从后端加载页面详情（含 schema）
  useEffect(() => {
    const fetchPage = async () => {
      setLoading(true);
      try {
        const res = await getPageDetail(id!);
        // 响应拦截器已解包, res 可能直接是 DTO 或 { data: DTO }
        const page = res?.name !== undefined ? res : res?.data;

        setPageName(page?.name || `页面 ${id}`);

        // json 可能是 JSON 字符串或已解析的对象
        let parsedJson = page?.json;
        if (typeof parsedJson === 'string' && parsedJson) {
          try {
            parsedJson = JSON.parse(parsedJson);
          } catch {
            // 如果 JSON 解析失败，使用默认骨架
            parsedJson = DEFAULT_JSON;
          }
        }
        const finalJson = parsedJson || DEFAULT_JSON;
        jsonRef.current = finalJson;
        setEditorValue(finalJson);
      } catch (err) {
        console.error('[Designer] 加载页面详情失败:', err);
        message.error('加载页面配置失败');
        jsonRef.current = DEFAULT_JSON;
        setEditorValue(DEFAULT_JSON);
        setPageName(`页面 ${id}`);
      } finally {
        setLoading(false);
      }
    };
    fetchPage();
  }, [id]);

  /** Editor onChange 仅更新 ref，不触发 re-render */
  const handleEditorChange = useCallback((value: any) => {
    jsonRef.current = value;
  }, []);

  /** 保存 Json 到后端 —— 从 ref 中读取最新快照 */
  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const currentJson = jsonRef.current;
      const jsonStr = typeof currentJson === 'string' ? currentJson : JSON.stringify(currentJson);
      await updatePageJson(id!, jsonStr);
      message.success('保存成功');
    } catch (err) {
      console.error('[Designer] 保存 Schema 失败:', err);
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  }, [id]);

  if (loading) {
    return (
      <div
        style={{
          height: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Spin size="large" tip="加载页面配置中..." />
      </div>
    );
  }

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      {/* ========== 顶部 Header（已隔离，不受编辑器 onChange 影响） ========== */}
      <DesignerHeader
        pageName={pageName}
        isPreview={isPreview}
        saving={saving}
        onBack={onBack}
        onTogglePreview={setIsPreview}
        onSave={handleSave}
      />

      {/* ========== 主体区：Amis Editor（懒加载 + Suspense） ========== */}
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        <Suspense
          fallback={
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
              <Spin size="large" tip="加载可视化编辑器..." />
            </div>
          }
        >
          <LazyEditor
            theme="cxd"
            preview={isPreview}
            value={editorValue}
            onChange={handleEditorChange}
            className="is-fixed"
            amisEnv={env}
          />
        </Suspense>
      </div>
    </div>
  );
};

export default Designer;

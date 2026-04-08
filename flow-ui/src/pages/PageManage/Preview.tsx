import React, { useState, useEffect, useMemo } from 'react';
import { useParams, history, request as umiRequest } from '@umijs/max';
import { message, Spin } from 'antd';

// ============================================================
// Amis 运行时样式（仅引入渲染器所需，不引入编辑器样式）
// ============================================================
import 'amis/lib/themes/cxd.css';
import 'amis/lib/helper.css';

import { render as renderAmis } from 'amis';

import { getPageDetail } from './services/pageManage';
import { createAmisEnv } from '@/utils/amisEnv';

/**
 * ================================================================
 * Amis Runtime 预览页
 *
 * 职责：
 *   1. 根据路由参数 :id 获取页面的 JSON Schema
 *   2. 通过 amis 的 render 函数将 Schema 渲染为真实 React DOM
 *   3. 通过 env 对象将项目 HTTP / 路由 / 通知能力桥接给 amis
 * ================================================================
 */
const Preview: React.FC = () => {
  const params = useParams<{ id: string }>();
  const [json, setJson] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  // ------------------------------------------------------------------
  // 从后端获取页面 Schema
  // ------------------------------------------------------------------
  useEffect(() => {
    const fetchSchema = async () => {
      setLoading(true);
      try {
        const res = await getPageDetail(params.id!);
        // 响应拦截器已解包，res 可能直接是 DTO 或 { data: DTO }
        const page = res?.name !== undefined ? res : res?.data;

        // json 可能是 JSON 字符串或已解析的对象
        let parsedJson = page?.json;
        if (typeof parsedJson === 'string' && parsedJson) {
          try {
            parsedJson = JSON.parse(parsedJson);
          } catch {
            parsedJson = null;
          }
        }
        setJson(parsedJson || null);
      } catch (err) {
        message.error('获取页面配置失败');
        console.error('[Preview] 获取 Schema 失败:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchSchema();
  }, [params.id]);

  // ==================================================================
  // 核心：Amis Env 环境配置（桥接项目能力给 Amis 运行时）
  //
  // amis 的 render(schema, props, env) 第三个参数 env 是一个"宿主环境"
  // 对象，amis 内部的所有 IO 操作（网络请求、消息提示、页面跳转）都
  // 通过 env 中的方法来执行。我们在此处将项目已有的基础设施注入进去。
  // ==================================================================
  const env = useMemo(() => createAmisEnv(), []);

  // ------------------------------------------------------------------
  // 渲染
  // ------------------------------------------------------------------
  if (loading) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#f5f5f5',
        }}
      >
        <Spin size="large" tip="加载页面中..." />
      </div>
    );
  }

  return (
    <div
      className="amis-runtime-container"
      style={{ minHeight: '100vh', background: '#f5f5f5' }}
    >
      {json ? (
        renderAmis(
          json,
          {},   // props: 可传入 data 作为初始数据域
          env,  // env:   宿主环境配置
        )
      ) : (
        <div style={{ textAlign: 'center', paddingTop: 200 }}>
          <p>未找到该页面的配置 (ID: {params.id})</p>
        </div>
      )}
    </div>
  );
};

export default Preview;

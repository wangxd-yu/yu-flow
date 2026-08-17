/**
 * 接口复制弹窗
 *
 * 复制范围由后端 /flow-api/api/{id}/copy 决定：实现内容、契约、防护、缓存、
 * 数据查看/导出配置、Excel 模板与开放平台授权全量克隆，副本一律为未发布草稿。
 * 前端只负责收集「新名称 / 目标目录 / 新 path」，并在目录变更时按新目录前缀重写 path。
 */
import React, { useEffect, useState } from 'react';
import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { Alert, Form, Space, Tag, message } from 'antd';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';
import {
  checkApiPathTaken,
  copyAutoApiConfig,
  type FlowController,
} from '@/services/flow/flowController';
import {
  fetchStackedDirectoryPathPrefix,
  normalizePathPrefix,
  rewritePathWithPrefix,
} from '@/utils/apiPathPrefix';

interface Props {
  open: boolean;
  source?: FlowController | null;
  onCancel: () => void;
  onSuccess: () => void;
}

type CopyFormValues = {
  name?: string;
  url?: string;
  directoryId?: string;
};

const METHOD_COLORS: Record<string, string> = {
  GET: 'blue',
  POST: 'green',
  PUT: 'orange',
  DELETE: 'red',
};

const ApiCopyModal: React.FC<Props> = ({ open, source, onCancel, onSuccess }) => {
  const [form] = Form.useForm<CopyFormValues>();
  const [conflict, setConflict] = useState(false);
  /** 当前 path 所基于的目录及其有效前缀；未就绪前不做重写 */
  const [baseline, setBaseline] = useState<{ dirId?: string; prefix: string } | null>(null);

  const isWrap = source?.interceptMode === 'WRAP' || source?.serviceType === 'HOST';
  const method = (source?.method || 'GET').toUpperCase();
  const directoryId = Form.useWatch('directoryId', form);
  const url = Form.useWatch('url', form);

  useEffect(() => {
    if (!open || !source) return;
    setConflict(false);
    setBaseline(null);
    // WRAP 的 path 指向宿主真实路由，保持原样，由用户决定是否调整
    form.setFieldsValue({
      name: `${source.name || ''}_副本`,
      url: isWrap ? source.url : `${source.url || ''}-copy`,
      directoryId: source.directoryId,
    });
    if (isWrap) {
      setBaseline({ dirId: source.directoryId, prefix: '' });
      return;
    }
    let cancelled = false;
    (async () => {
      let prefix = '';
      try {
        prefix = await fetchStackedDirectoryPathPrefix(source.directoryId);
      } catch {
        /* 取不到前缀时按无前缀处理 */
      }
      if (!cancelled) setBaseline({ dirId: source.directoryId, prefix });
    })();
    return () => {
      cancelled = true;
    };
  }, [open, source]);

  // 切换目标目录时，按新目录的有效前缀重写 path，保留相对段
  useEffect(() => {
    if (!open || isWrap || !baseline || directoryId === baseline.dirId) return;
    let cancelled = false;
    (async () => {
      let newPrefix = '';
      try {
        newPrefix = await fetchStackedDirectoryPathPrefix(directoryId);
      } catch {
        return;
      }
      if (cancelled) return;
      const current = form.getFieldValue('url');
      const rewritten = rewritePathWithPrefix(current, baseline.prefix, newPrefix);
      setBaseline({ dirId: directoryId, prefix: newPrefix });
      if (rewritten && normalizePathPrefix(rewritten) !== normalizePathPrefix(current)) {
        form.setFieldsValue({ url: rewritten });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, isWrap, baseline, directoryId]);

  useEffect(() => {
    if (!open || !url?.trim()) {
      setConflict(false);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const taken = await checkApiPathTaken(method, url.trim());
        if (!cancelled) setConflict(taken);
      } catch {
        if (!cancelled) setConflict(false);
      }
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [open, url, method]);

  return (
    <ModalForm<CopyFormValues>
      title="复制接口"
      width={560}
      form={form}
      open={open}
      onOpenChange={(visible) => {
        if (!visible) onCancel();
      }}
      modalProps={{ destroyOnClose: true, okText: '确认复制' }}
      onFinish={async (values) => {
        const name = values.name?.trim();
        const targetUrl = values.url?.trim();
        if (!source?.id || !name || !targetUrl) {
          message.warning('请填写接口名称与路径');
          return false;
        }
        if (conflict) {
          message.warning('接口路径与已发布接口冲突，请修改后再复制');
          return false;
        }
        try {
          await copyAutoApiConfig(source.id, {
            name,
            url: targetUrl,
            directoryId: values.directoryId,
          });
          message.success('复制成功，副本为未发布草稿');
          onSuccess();
          return true;
        } catch (error: any) {
          if (!error?.message) {
            message.error('复制失败，请重试');
          }
          return false;
        }
      }}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          <Space size={6} wrap>
            源接口
            <Tag color={METHOD_COLORS[method]} style={{ margin: 0 }}>
              {method}
            </Tag>
            <span style={{ fontFamily: 'monospace' }}>{source?.url}</span>
          </Space>
        }
        description="实现内容、契约、防护、缓存、导出配置、Excel 模板与开放平台授权都会一并复制；副本为未发布草稿，需自行发布后才对外服务。"
      />
      <ProFormText
        name="name"
        label="新接口名称"
        placeholder="请输入副本名称"
        rules={[{ required: true, message: '请输入接口名称' }]}
      />
      <DirectoryTreeSelect
        bizType="api"
        name="directoryId"
        label="目标目录"
        placeholder="不选则沿用源接口目录"
      />
      <ProFormText
        name="url"
        label="新接口路径"
        placeholder="例如 /api/user/list-copy"
        tooltip={
          isWrap
            ? '包裹模式的 path 需与宿主真实路由一致，切换目录不会重写'
            : '切换目标目录时会按新目录的有效前缀自动重写，相对段保留'
        }
        rules={[{ required: true, message: '请输入接口路径' }]}
        fieldProps={{ status: conflict ? 'error' : undefined }}
        extra={
          conflict ? (
            <span style={{ color: '#ff4d4f' }}>
              {method} {url} 与已发布接口冲突，请修改
            </span>
          ) : undefined
        }
      />
    </ModalForm>
  );
};

export default ApiCopyModal;

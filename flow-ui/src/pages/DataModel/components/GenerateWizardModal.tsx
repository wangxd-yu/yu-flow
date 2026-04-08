/**
 * 生成页面与 API 配置向导弹窗
 *
 * 功能：
 *  1. 用户确认模块名、页面名称、页面路径、API 根路径、目标目录
 *  2. moduleName 变化时联动自动更新 pageName / pagePath / apiBasePath
 *  3. pagePath 和 apiBasePath 带有防抖异步校验（查重）
 *  4. 提交时按顺序：批量创建 API → 生成 Amis Schema → 保存页面 → 打开设计器
 */

import React, { useCallback, useRef } from 'react';
import { ModalForm, ProFormText, ProFormDependency } from '@ant-design/pro-components';
import { message } from 'antd';
import type { FormInstance } from 'antd';
import { request } from '@umijs/max';
import DirectoryTreeSelect from '@/components/DirectoryTreeSelect';

import { generateAmisSchema, DataModel } from '@/utils/amisGenerator';
import { generateCrudApis } from '@/utils/apiGenerator';
import { addPage } from '@/pages/PageManage/services/pageManage';

// ================================================================
// 类型定义
// ================================================================

export interface GenerateWizardProps {
  /** 是否显示 */
  open: boolean;
  /** 关闭回调 */
  onOpenChange: (visible: boolean) => void;
  /** 当前操作的模型（需含完整的 fieldsSchema） */
  model: any;
  /** 生成成功后回调，传回新页面 ID */
  onSuccess?: (pageId: string) => void;
}

// ================================================================
// 防抖异步校验辅助
// ================================================================

/**
 * 创建一个带防抖的异步 validator，用于 Ant Design Form rules
 *
 * @param checkFn   校验函数，接收当前值，返回 boolean（true = 被占用 / 冲突）
 * @param errorMsg  冲突时的错误提示
 * @param delay     防抖毫秒数，默认 500ms
 */
function useDebouncedValidator(
  checkFn: (value: string) => Promise<boolean>,
  errorMsg: string,
  delay: number = 500,
) {
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const validator = useCallback(
    (_: any, value: string) => {
      // 空值由 required 规则处理
      if (!value) return Promise.resolve();

      return new Promise<void>((resolve, reject) => {
        // 清除上一次防抖定时器
        if (timerRef.current) clearTimeout(timerRef.current);

        timerRef.current = setTimeout(async () => {
          try {
            const occupied = await checkFn(value);
            if (occupied) {
              reject(errorMsg);
            } else {
              resolve();
            }
          } catch {
            // 网络异常等情况，不阻断用户
            resolve();
          }
        }, delay);
      });
    },
    [checkFn, errorMsg, delay],
  );

  return validator;
}

// ================================================================
// 组件
// ================================================================

const GenerateWizardModal: React.FC<GenerateWizardProps> = ({
  open,
  onOpenChange,
  model,
  onSuccess,
}) => {
  const formRef = useRef<FormInstance>();

  // ---- 异步校验：页面路径查重 ----
  const checkPagePath = useCallback(async (path: string) => {
    const res = await request<any>('/flow-api/pages/check-path', {
      method: 'GET',
      params: { path },
    });
    // 后端 R<Boolean> → res.data 或直接 res 为 boolean
    return res?.data === true || res === true;
  }, []);

  const pagePathValidator = useDebouncedValidator(
    checkPagePath,
    '该页面路由已被占用',
  );

  // ---- 异步校验：API 路径查重 ----
  const checkApiUrl = useCallback(async (baseUrl: string) => {
    const cleanBaseUrl = baseUrl ? baseUrl.split('?')[0] : baseUrl;
    const res = await request<any>('/flow-api/api/check-url', {
      method: 'GET',
      params: { baseUrl: cleanBaseUrl },
    });
    return res?.data === true || res === true;
  }, []);

  const apiPathValidator = useDebouncedValidator(
    checkApiUrl,
    '该 API 根路径下已存在冲突接口',
  );

  // ---- 联动：moduleName 变化时自动填充其他字段 ----
  const handleValuesChange = (changedValues: any) => {
    if (changedValues.moduleName !== undefined) {
      const name = changedValues.moduleName;
      formRef.current?.setFieldsValue({
        pageName: name ? `${name}管理` : '',
      });
      // pagePath 和 apiBasePath 不随 moduleName 联动（它们基于 tableName，不变）
    }
  };

  // ---- 提交逻辑 ----
  const handleFinish = async (values: any) => {
    const { moduleName, pageName, pagePath, apiBasePath, directoryId } = values;
    const targetDirId = directoryId || '';

    // 1. 生成 5 个 CRUD API 配置（使用用户确认的 apiBasePath）
    const hideApi = message.loading('正在批量创建 API...', 0);
    try {
      const apis = generateCrudApis(
        model as DataModel,
        targetDirId,
        model.datasource || '[DEFAULT]',
        apiBasePath,                     // 传入自定义根路径
      );

      await request('/flow-api/api/batch/create', {
        method: 'POST',
        data: apis,
      });
    } catch (apiErr: any) {
      hideApi();
      message.error('批量创建 API 失败：' + (apiErr?.message || ''));
      return false;
    }
    hideApi();
    message.success('API 批量创建成功');

    // 2. 生成 Amis 页面 Schema（使用 getAmisApiPrefix() + apiBasePath）
    const { getAmisApiPrefix } = await import('@/utils/env');
    const amisBaseUrl = `${getAmisApiPrefix()}${apiBasePath}`;
    const defaultTraits = ['page', 'detail', 'insert', 'update', 'delete'];
    const traitApiMap = {
      page: `${amisBaseUrl}/page`,
      detail: `${amisBaseUrl}/detail?id=\${id}`,
      insert: `${amisBaseUrl}`,
      update: `${amisBaseUrl}?id=\${id}`,
      delete: `${amisBaseUrl}?id=\${id}`,
      bulkDelete: `${amisBaseUrl}?id=\${ids|raw}`,
    };
    const schema = generateAmisSchema(model as DataModel, defaultTraits, traitApiMap);

    // 3. 保存页面
    try {
      const pageData = {
        name: pageName,
        routePath: pagePath,
        json: JSON.stringify(schema),
        status: 0,
        directoryId: directoryId || null,
      };
      const res = await addPage(pageData);
      message.success('生成页面成功！');

      const newPageId = res?.data?.id || res?.id;
      if (newPageId && onSuccess) {
        onSuccess(newPageId);
      }
    } catch (e: any) {
      message.error('保存页面失败：' + (e?.message || ''));
      return false;
    }

    return true;
  };

  // ---- 初始值 ----
  const initialValues = {
    moduleName: model?.name || '',
    pageName: model?.name ? `${model.name}管理` : '',
    pagePath: model?.tableName ? `/pages/${model.tableName}` : '',
    apiBasePath: model?.tableName ? `/dynamic/${model.tableName}` : '',
    directoryId: model?.directoryId || undefined,
  };

  return (
    <ModalForm
      title="⚡ 生成页面与 API 配置向导"
      width={580}
      open={open}
      onOpenChange={onOpenChange}
      formRef={formRef}
      initialValues={initialValues}
      modalProps={{
        destroyOnClose: true,
        maskClosable: false,
      }}
      onValuesChange={handleValuesChange}
      onFinish={handleFinish}
      submitter={{
        searchConfig: { submitText: '确认生成', resetText: '取消' },
      }}
    >
      {/* ==================== 基础配置区 ==================== */}
      <ProFormText
        name="moduleName"
        label="模块名称"
        rules={[{ required: true, message: '模块名称为必填项' }]}
        placeholder="例如：学生"
        tooltip="修改后将联动更新「页面名称」"
        fieldProps={{ autoComplete: 'off' }}
      />

      <DirectoryTreeSelect
        name="directoryId"
        label="目标目录"
        rules={[{ required: true, message: '请选择目标目录' }]}
        placeholder="请选择生成内容的目标目录"
      />

      {/* ==================== 路由与命名配置区 ==================== */}
      <ProFormText
        name="pageName"
        label="页面名称"
        rules={[{ required: true, message: '页面名称为必填项' }]}
        placeholder="例如：学生管理"
        tooltip="联动自 moduleName，格式：${moduleName}管理"
        fieldProps={{ autoComplete: 'off' }}
      />

      <ProFormText
        name="pagePath"
        label="页面访问路径"
        rules={[
          { required: true, message: '页面访问路径为必填项' },
          {
            pattern: /^\/[a-zA-Z0-9_\-/]+$/,
            message: '路径必须以 / 开头，只能包含英文、数字、下划线和横线',
          },
          { validator: pagePathValidator },
        ]}
        placeholder="例如：/pages/student"
        tooltip="用于前端路由访问，将异步校验是否已被占用"
        fieldProps={{
          autoComplete: 'off',
        }}
      />

      <ProFormText
        name="apiBasePath"
        label="API 根路径"
        rules={[
          { required: true, message: 'API 根路径为必填项' },
          {
            pattern: /^\/[a-zA-Z0-9_\-/]+$/,
            message: '路径必须以 / 开头，只能包含英文、数字、下划线和横线',
          },
          { validator: apiPathValidator },
        ]}
        placeholder="例如：/dynamic/student"
        tooltip="系统将基于此路径生成 5 个 CRUD 接口，将异步校验是否冲突"
        fieldProps={{
          autoComplete: 'off',
        }}
      />
    </ModalForm>
  );
};

export default GenerateWizardModal;

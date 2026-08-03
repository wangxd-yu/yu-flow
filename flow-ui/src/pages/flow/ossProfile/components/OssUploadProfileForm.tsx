import {
  DrawerForm,
  ProFormDependency,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { message } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';

import { queryOssConnectionOptions } from '@/services/flow/ossConnection';
import {
  createOssUploadProfile,
  OssUploadProfile,
  updateOssUploadProfile,
} from '@/services/flow/ossUploadProfile';

export type OssUploadProfileFormProps = {
  onCancel: () => void;
  onSubmit: (success: boolean) => Promise<boolean>;
  modalVisible: boolean;
  values?: Partial<OssUploadProfile>;
  isEdit?: boolean;
};

const OssUploadProfileForm: React.FC<OssUploadProfileFormProps> = (props) => {
  const { modalVisible, onCancel, onSubmit, values = {}, isEdit } = props;
  const [connectionOptions, setConnectionOptions] = useState<
    { label: string; value: string }[]
  >([]);

  useEffect(() => {
    if (!modalVisible) return;
    (async () => {
      try {
        const list = await queryOssConnectionOptions();
        const items = Array.isArray(list) ? list : [];
        setConnectionOptions(
          items.map((item) => ({
            label: `${item.name} (${item.code})`,
            value: item.code,
          })),
        );
      } catch {
        setConnectionOptions([]);
      }
    })();
  }, [modalVisible]);

  const initialValues = useMemo(
    () => ({
      visibility: 'PRIVATE',
      requireAuth: true,
      enabled: true,
      maxFilesPerRequest: 1,
      thumbnailEnabled: false,
      ...values,
      enabled: values.enabled === undefined ? true : Boolean(Number(values.enabled)),
      requireAuth:
        values.requireAuth === undefined ? true : Boolean(Number(values.requireAuth)),
      thumbnailEnabled:
        values.thumbnailEnabled === undefined
          ? false
          : Boolean(Number(values.thumbnailEnabled)),
    }),
    [values],
  );

  const handleSubmit = async (formValues: any) => {
    const hide = message.loading(isEdit ? '正在更新...' : '正在添加...');
    try {
      if (formValues.bizFieldsSchema?.trim()) {
        JSON.parse(formValues.bizFieldsSchema);
      }
      // 空值用 0 表示「回退全局」，便于编辑时清空场景覆盖
      const payload = {
        ...formValues,
        thumbnailMaxEdge:
          formValues.thumbnailMaxEdge == null || formValues.thumbnailMaxEdge === ''
            ? 0
            : formValues.thumbnailMaxEdge,
        thumbnailMaxSourceBytes:
          formValues.thumbnailMaxSourceBytes == null ||
          formValues.thumbnailMaxSourceBytes === ''
            ? 0
            : formValues.thumbnailMaxSourceBytes,
        thumbnailJpegQuality:
          formValues.thumbnailJpegQuality == null || formValues.thumbnailJpegQuality === ''
            ? 0
            : formValues.thumbnailJpegQuality,
      };
      if (isEdit) {
        await updateOssUploadProfile(values.id!, payload);
      } else {
        await createOssUploadProfile(payload);
      }
      hide();
      message.success(isEdit ? '更新成功' : '添加成功');
      onSubmit(true);
      return true;
    } catch (e: any) {
      hide();
      if (e instanceof SyntaxError) {
        message.error('业务字段 Schema 不是合法 JSON');
        return false;
      }
      if (!e?.message?.includes('DEMO_RESTRICTED')) {
        message.error(e?.message || (isEdit ? '更新失败' : '添加失败'));
      }
      return false;
    }
  };

  return (
    <DrawerForm
      title={isEdit ? '编辑上传场景' : '新建上传场景'}
      width="60%"
      layout="horizontal"
      open={modalVisible}
      onOpenChange={(visible) => {
        if (!visible) {
          onCancel();
        }
      }}
      initialValues={initialValues}
      onFinish={handleSubmit}
      drawerProps={{
        destroyOnClose: true,
      }}
      labelCol={{ style: { width: '130px' } }}
      grid={true}
      rowProps={{
        gutter: [16, 16],
      }}
      submitter={{
        searchConfig: {
          submitText: '提交',
          resetText: '取消',
        },
      }}
    >
      <ProFormText
        name="name"
        label="场景名称"
        placeholder="请输入场景名称"
        rules={[{ required: true, message: '请输入场景名称!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="code"
        label="场景编码"
        placeholder="如 avatar_upload"
        tooltip="上传 API 通过 profile 参数引用，创建后不可修改"
        disabled={isEdit}
        rules={
          isEdit
            ? []
            : [
                { required: true, message: '请输入场景编码!' },
                {
                  pattern: /^[a-z][a-z0-9_]*$/,
                  message: '只能包含小写字母、数字和下划线，且不能以数字开头',
                },
                { max: 50, message: '编码长度不能超过50个字符' },
              ]
        }
        colProps={{ span: 12 }}
      />
      <ProFormSelect
        name="connectionCode"
        label="OSS 连接"
        options={connectionOptions}
        rules={[{ required: true, message: '请选择 OSS 连接!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormSelect
        name="visibility"
        label="可见性"
        valueEnum={{
          PUBLIC: '公有',
          PRIVATE: '私有',
        }}
        rules={[{ required: true, message: '请选择可见性!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="bucketOverride"
        label="桶覆盖"
        placeholder="可选，覆盖连接默认桶"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="keyPattern"
        label="Key 模式"
        placeholder="如 {yyyy}/{MM}/{uuid}.{ext}"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="allowedContentTypes"
        label="允许 MIME"
        placeholder="逗号分隔，如 image/png,image/jpeg"
        colProps={{ span: 12 }}
      />
      <ProFormText
        name="allowedExtensions"
        label="允许扩展名"
        placeholder="逗号分隔，如 png,jpg,pdf"
        colProps={{ span: 12 }}
      />
      <ProFormDigit
        name="maxSizeBytes"
        label="最大字节数"
        placeholder="可选"
        min={0}
        fieldProps={{ precision: 0 }}
        colProps={{ span: 12 }}
      />
      <ProFormDigit
        name="maxFilesPerRequest"
        label="单次最多文件数"
        min={1}
        fieldProps={{ precision: 0 }}
        colProps={{ span: 12 }}
      />
      <ProFormDigit
        name="quotaMaxBytes"
        label="场景容量配额(字节)"
        placeholder="空=不限"
        min={0}
        fieldProps={{ precision: 0 }}
        colProps={{ span: 12 }}
      />
      <ProFormDigit
        name="quotaMaxFiles"
        label="场景文件数配额"
        placeholder="空=不限"
        min={0}
        fieldProps={{ precision: 0 }}
        colProps={{ span: 12 }}
      />
      <ProFormSwitch name="requireAuth" label="要求登录" colProps={{ span: 12 }} />
      <ProFormSwitch
        name="thumbnailEnabled"
        label="生成缩略图"
        tooltip="按本业务场景开启；边长/质量可单独配置，未填则用全局默认"
        colProps={{ span: 12 }}
      />
      <ProFormDependency name={['thumbnailEnabled']}>
        {({ thumbnailEnabled }) =>
          thumbnailEnabled ? (
            <>
              <ProFormDigit
                name="thumbnailMaxEdge"
                label="缩略图最长边"
                placeholder="空=全局默认 256"
                min={16}
                max={4096}
                fieldProps={{ precision: 0 }}
                colProps={{ span: 12 }}
                tooltip="像素；头像场景可设 128，相册可设 512"
              />
              <ProFormDigit
                name="thumbnailMaxSourceBytes"
                label="缩略图源文件上限"
                placeholder="空=全局默认"
                min={0}
                fieldProps={{ precision: 0 }}
                colProps={{ span: 12 }}
                tooltip="超过则跳过生成（字节）"
              />
              <ProFormDigit
                name="thumbnailJpegQuality"
                label="缩略图 JPEG 质量"
                placeholder="空=全局默认 0.85"
                min={0.1}
                max={1}
                fieldProps={{ step: 0.05, precision: 2 }}
                colProps={{ span: 12 }}
              />
            </>
          ) : null
        }
      </ProFormDependency>
      <ProFormText
        name="accessPerm"
        label="访问权限码"
        placeholder="可选，如 flow:oss:download"
        colProps={{ span: 12 }}
      />
      <ProFormSelect
        name="enabled"
        label="状态"
        options={[
          { label: '启用', value: true },
          { label: '停用', value: false },
        ]}
        rules={[{ required: true, message: '请选择状态!' }]}
        colProps={{ span: 12 }}
      />
      <ProFormTextArea
        name="bizFieldsSchema"
        label="业务字段 Schema"
        placeholder='可选 JSON，如 {"orderId":{"type":"string","required":true}}'
        fieldProps={{ rows: 4 }}
        colProps={{ span: 24 }}
      />
      <ProFormTextArea
        name="remark"
        label="备注"
        placeholder="可选"
        fieldProps={{ rows: 2 }}
        colProps={{ span: 24 }}
      />
    </DrawerForm>
  );
};

export default OssUploadProfileForm;

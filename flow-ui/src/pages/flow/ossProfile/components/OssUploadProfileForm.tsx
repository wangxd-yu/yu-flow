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

const MB = 1024 * 1024;

const MIME_OPTIONS = [
  { label: 'image/jpeg', value: 'image/jpeg' },
  { label: 'image/png', value: 'image/png' },
  { label: 'image/gif', value: 'image/gif' },
  { label: 'image/webp', value: 'image/webp' },
  { label: 'image/bmp', value: 'image/bmp' },
  { label: 'image/svg+xml', value: 'image/svg+xml' },
  { label: 'application/pdf', value: 'application/pdf' },
  { label: 'application/zip', value: 'application/zip' },
  { label: 'application/json', value: 'application/json' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/csv', value: 'text/csv' },
  {
    label: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (xlsx)',
    value: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  },
  {
    label: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document (docx)',
    value: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  },
  { label: 'video/mp4', value: 'video/mp4' },
  { label: 'audio/mpeg', value: 'audio/mpeg' },
];

const EXT_OPTIONS = [
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'txt', 'csv', 'json', 'zip', 'rar', '7z',
  'mp4', 'mp3', 'wav',
].map((ext) => ({ label: ext, value: ext }));

/** 逗号串 → 多选数组 */
function splitCsv(raw?: string | null): string[] | undefined {
  if (!raw?.trim()) return undefined;
  const list = raw
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean);
  return list.length ? list : undefined;
}

/** 多选数组 → 逗号串（空则空串） */
function joinCsv(list?: string[] | null): string {
  if (!list?.length) return '';
  return list.map((s) => String(s).trim()).filter(Boolean).join(',');
}

/** 字节 → MB 表单值；空保持空 */
function bytesToMb(bytes?: number | null): number | undefined {
  if (bytes == null || bytes === 0) return undefined;
  const mb = bytes / MB;
  return Math.round(mb * 1000) / 1000;
}

/** MB → 字节；空/0 → 0（便于后端清空可选上限） */
function mbToBytes(mb?: number | null): number {
  if (mb == null || Number.isNaN(Number(mb)) || Number(mb) <= 0) return 0;
  return Math.round(Number(mb) * MB);
}

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
      maxSizeMb: bytesToMb(values.maxSizeBytes),
      quotaMaxMb: bytesToMb(values.quotaMaxBytes),
      thumbnailMaxSourceMb: bytesToMb(values.thumbnailMaxSourceBytes),
      allowedContentTypes: splitCsv(values.allowedContentTypes),
      allowedExtensions: splitCsv(values.allowedExtensions),
    }),
    [values],
  );

  const handleSubmit = async (formValues: any) => {
    const hide = message.loading(isEdit ? '正在更新...' : '正在添加...');
    try {
      if (formValues.bizFieldsSchema?.trim()) {
        JSON.parse(formValues.bizFieldsSchema);
      }
      const {
        maxSizeMb,
        quotaMaxMb,
        thumbnailMaxSourceMb,
        maxSizeBytes: _dropMax,
        quotaMaxBytes: _dropQuota,
        thumbnailMaxSourceBytes: _dropThumbSrc,
        ...rest
      } = formValues;
      // 空值用 0 表示「不限 / 回退全局」，便于编辑时清空
      const payload = {
        ...rest,
        allowedContentTypes: joinCsv(formValues.allowedContentTypes),
        allowedExtensions: joinCsv(formValues.allowedExtensions),
        maxSizeBytes: mbToBytes(maxSizeMb),
        quotaMaxBytes: mbToBytes(quotaMaxMb),
        thumbnailMaxEdge:
          formValues.thumbnailMaxEdge == null || formValues.thumbnailMaxEdge === ''
            ? 0
            : formValues.thumbnailMaxEdge,
        thumbnailMaxSourceBytes: mbToBytes(thumbnailMaxSourceMb),
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
      title={isEdit ? '编辑上传配置' : '新建上传配置'}
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
        placeholder="{profile}/{yyyy}/{MM}/{uuid}_{filename}"
        colProps={{ span: 24 }}
        tooltip="对象键模板；连接上的 Key 前缀会再拼在最前面"
        extra={
          <span>
            可用占位符：
            <code>{'{profile}'}</code> 场景编码、
            <code>{'{yyyy}'}</code> 年、
            <code>{'{MM}'}</code> 月、
            <code>{'{dd}'}</code> 日、
            <code>{'{HH}'}</code> 时、
            <code>{'{mm}'}</code> 分、
            <code>{'{ss}'}</code> 秒、
            <code>{'{uuid}'}</code>、
            <code>{'{filename}'}</code> 完整文件名、
            <code>{'{name}'}</code> 不含扩展名、
            <code>{'{ext}'}</code> 扩展名(小写无点)。
            默认：<code>{'{profile}/{yyyy}/{MM}/{uuid}_{filename}'}</code>
          </span>
        }
      />
      <ProFormSelect
        name="allowedContentTypes"
        label="允许 MIME"
        mode="tags"
        options={MIME_OPTIONS}
        placeholder="下拉多选，也可输入自定义后回车"
        colProps={{ span: 12 }}
        fieldProps={{
          tokenSeparators: [',', '，', ' '],
          maxTagCount: 'responsive',
        }}
        tooltip="空=不限制；提交时按逗号写入后端"
      />
      <ProFormSelect
        name="allowedExtensions"
        label="允许扩展名"
        mode="tags"
        options={EXT_OPTIONS}
        placeholder="下拉多选，也可输入自定义后回车"
        colProps={{ span: 12 }}
        fieldProps={{
          tokenSeparators: [',', '，', ' '],
          maxTagCount: 'responsive',
        }}
        tooltip="不含点，如 png；空=不限制"
      />
      <ProFormDigit
        name="maxSizeMb"
        label="单文件上限"
        placeholder="空=不限"
        min={0}
        fieldProps={{ precision: 3, addonAfter: 'MB' }}
        colProps={{ span: 12 }}
        tooltip="后端仍按字节存储；勿超过 spring.servlet.multipart.max-file-size（默认 50MB）"
      />
      <ProFormDigit
        name="maxFilesPerRequest"
        label="单次最多文件数"
        min={1}
        fieldProps={{ precision: 0 }}
        colProps={{ span: 12 }}
      />
      <ProFormDigit
        name="quotaMaxMb"
        label="场景容量配额"
        placeholder="空=不限"
        min={0}
        fieldProps={{ precision: 3, addonAfter: 'MB' }}
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
                name="thumbnailMaxSourceMb"
                label="缩略图源文件上限"
                placeholder="空=全局默认"
                min={0}
                fieldProps={{ precision: 3, addonAfter: 'MB' }}
                colProps={{ span: 12 }}
                tooltip="超过则跳过生成"
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

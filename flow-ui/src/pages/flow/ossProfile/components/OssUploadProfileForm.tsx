import {
  DrawerForm,
  ProFormDependency,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { AutoComplete, Button, Card, Col, Collapse, Form, Row, Space, Tag, message } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';

import OssAccessRulesFields from '@/pages/flow/ossProfile/components/OssAccessRulesFields';
import { queryOssConnectionOptions, queryOssConnectionBuckets, OssConnection } from '@/services/flow/ossConnection';
import {
  createOssUploadProfile,
  OssUploadProfile,
  updateOssUploadProfile,
} from '@/services/flow/ossUploadProfile';
import {
  buildOssAccessRulesJson,
  parseOssAccessRules,
  personalFilesPreset,
} from '@/utils/ossAccessRules';
import './OssUploadProfileForm.less';

const MB = 1024 * 1024;

const PRESET_SCENARIOS = [
  {
    key: 'IMAGE',
    label: '🖼️ 常用图片',
    mimeTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp', 'image/svg+xml'],
    exts: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'],
  },
  {
    key: 'DOCUMENT',
    label: '📄 常用文档',
    mimeTypes: [
      'application/pdf',
      'text/plain',
      'text/csv',
      'application/json',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/msword',
      'application/vnd.ms-excel',
    ],
    exts: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'txt', 'csv', 'json'],
  },
  {
    key: 'ARCHIVE',
    label: '📦 压缩包',
    mimeTypes: [
      'application/zip',
      'application/x-rar-compressed',
      'application/x-7z-compressed',
      'application/x-tar',
      'application/gzip',
    ],
    exts: ['zip', 'rar', '7z', 'tar', 'gz'],
  },
  {
    key: 'MEDIA',
    label: '🎥 音视频',
    mimeTypes: ['video/mp4', 'audio/mpeg', 'audio/wav', 'video/x-msvideo'],
    exts: ['mp4', 'mp3', 'wav', 'avi'],
  },
];

const MIME_OPTIONS = [
  { label: 'image/jpeg', value: 'image/jpeg' },
  { label: 'image/png', value: 'image/png' },
  { label: 'image/gif', value: 'image/gif' },
  { label: 'image/webp', value: 'image/webp' },
  { label: 'image/bmp', value: 'image/bmp' },
  { label: 'image/svg+xml', value: 'image/svg+xml' },
  { label: 'application/pdf', value: 'application/pdf' },
  { label: 'application/msword (doc)', value: 'application/msword' },
  { label: 'application/vnd.ms-excel (xls)', value: 'application/vnd.ms-excel' },
  { label: 'application/vnd.ms-powerpoint (ppt)', value: 'application/vnd.ms-powerpoint' },
  {
    label: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document (docx)',
    value: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  },
  {
    label: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (xlsx)',
    value: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  },
  {
    label: 'application/vnd.openxmlformats-officedocument.presentationml.presentation (pptx)',
    value: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  },
  { label: 'application/zip', value: 'application/zip' },
  { label: 'application/x-rar-compressed', value: 'application/x-rar-compressed' },
  { label: 'application/x-7z-compressed', value: 'application/x-7z-compressed' },
  { label: 'application/json', value: 'application/json' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/csv', value: 'text/csv' },
  { label: 'video/mp4', value: 'video/mp4' },
  { label: 'audio/mpeg', value: 'audio/mpeg' },
  { label: 'audio/wav', value: 'audio/wav' },
];

const EXT_OPTIONS = [
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'txt', 'csv', 'json', 'zip', 'rar', '7z',
  'mp4', 'mp3', 'wav',
].map((ext) => ({ label: ext, value: ext }));

/** 扩展名 → 常见 MIME。选扩展名时带出；去掉扩展名时只收回它带出的项。 */
const EXT_TO_MIMES: Record<string, string[]> = {
  jpg: ['image/jpeg'],
  jpeg: ['image/jpeg'],
  png: ['image/png'],
  gif: ['image/gif'],
  webp: ['image/webp'],
  bmp: ['image/bmp'],
  svg: ['image/svg+xml'],
  pdf: ['application/pdf'],
  doc: ['application/msword'],
  docx: ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
  xls: ['application/vnd.ms-excel'],
  xlsx: ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
  ppt: ['application/vnd.ms-powerpoint'],
  pptx: ['application/vnd.openxmlformats-officedocument.presentationml.presentation'],
  txt: ['text/plain'],
  csv: ['text/csv'],
  json: ['application/json'],
  zip: ['application/zip'],
  rar: ['application/x-rar-compressed'],
  '7z': ['application/x-7z-compressed'],
  tar: ['application/x-tar'],
  gz: ['application/gzip'],
  mp4: ['video/mp4'],
  mp3: ['audio/mpeg'],
  wav: ['audio/wav'],
  avi: ['video/x-msvideo'],
};

function normalizeExt(raw: string): string {
  return String(raw || '')
    .trim()
    .replace(/^\./, '')
    .toLowerCase();
}

function uniqueStrings(list: string[]): string[] {
  return Array.from(new Set(list.map((s) => String(s).trim()).filter(Boolean)));
}

function mimesForExts(exts: string[]): string[] {
  const out: string[] = [];
  for (const ext of exts) {
    const mapped = EXT_TO_MIMES[normalizeExt(ext)];
    if (mapped) {
      out.push(...mapped);
    }
  }
  return uniqueStrings(out);
}

/** MIME = 当前扩展名推导值 + 用户额外手填（去掉扩展名时不误删手填项）。 */
function mergeMimesFromExts(currentMimes: string[], prevExts: string[], nextExts: string[]): string[] {
  const impliedBefore = new Set(mimesForExts(prevExts));
  const extras = (currentMimes || []).filter((m) => m && !impliedBefore.has(m));
  return uniqueStrings([...extras, ...mimesForExts(nextExts)]);
}

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

const BucketLoader: React.FC<{
  connectionCode?: string;
  onLoad: (code: string) => void;
}> = ({ connectionCode, onLoad }) => {
  useEffect(() => {
    if (connectionCode) {
      onLoad(connectionCode);
    }
  }, [connectionCode, onLoad]);
  return null;
};

const OssUploadProfileForm: React.FC<OssUploadProfileFormProps> = (props) => {
  const { modalVisible, onCancel, onSubmit, values = {}, isEdit } = props;
  const [form] = Form.useForm();
  const [rawConnections, setRawConnections] = useState<OssConnection[]>([]);
  const [connectionOptions, setConnectionOptions] = useState<
    { label: string; value: string }[]
  >([]);
  const [minioBucketsMap, setMinioBucketsMap] = useState<Record<string, string[]>>({});
  const [loadingMap, setLoadingMap] = useState<Record<string, boolean>>({});
  const fetchedCodesRef = React.useRef<Set<string>>(new Set());
  const connectionsRequestRef = useRef(0);
  const prevExtsRef = useRef<string[]>([]);
  const extractPrevExtsRef = useRef<string[]>([]);

  const loadMinioBuckets = React.useCallback(async (code: string) => {
    if (!code || fetchedCodesRef.current.has(code)) return;
    fetchedCodesRef.current.add(code);
    setLoadingMap((prev) => ({ ...prev, [code]: true }));
    try {
      const list = await queryOssConnectionBuckets(code);
      const bucketList = Array.isArray(list) ? list : (list as any)?.data || [];
      if (Array.isArray(bucketList)) {
        setMinioBucketsMap((prev) => ({ ...prev, [code]: bucketList }));
      }
    } catch (e) {
      fetchedCodesRef.current.delete(code);
      console.warn('Failed to query MinIO buckets:', code, e);
    } finally {
      setLoadingMap((prev) => ({ ...prev, [code]: false }));
    }
  }, []);

  useEffect(() => {
    if (!modalVisible) {
      connectionsRequestRef.current += 1;
      fetchedCodesRef.current.clear();
      return;
    }
    const requestId = ++connectionsRequestRef.current;
    (async () => {
      try {
        const list = await queryOssConnectionOptions();
        if (requestId !== connectionsRequestRef.current) return;
        const items = Array.isArray(list) ? list : [];
        setRawConnections(items);
        setConnectionOptions(
          items.map((item) => ({
            label: `${item.name} (${item.code})`,
            value: item.code,
          })),
        );
        if (values?.connectionCode) {
          loadMinioBuckets(values.connectionCode);
        }
      } catch {
        if (requestId !== connectionsRequestRef.current) return;
        setRawConnections([]);
        setConnectionOptions([]);
      }
    })();
  }, [modalVisible, values.connectionCode, loadMinioBuckets]);

  const initialValues = useMemo(
    () => {
      const parsed = parseOssAccessRules(values.callerPolicy);
      const accessRules =
        parsed.length > 0
          ? parsed
          : isEdit
            ? []
            : personalFilesPreset();
      return {
      visibility: 'PRIVATE',
      maxFilesPerRequest: 1,
      ...values,
      enabled: values.enabled === undefined ? true : Boolean(Number(values.enabled)),
      requireAuth:
        values.requireAuth === undefined ? true : Boolean(Number(values.requireAuth)),
      presignUploadEnabled:
        values.presignUploadEnabled === undefined
          ? false
          : Boolean(Number(values.presignUploadEnabled)),
      thumbnailEnabled:
        values.thumbnailEnabled === undefined
          ? false
          : Boolean(Number(values.thumbnailEnabled)),
      extractArchiveEnabled:
        values.extractArchiveEnabled === undefined
          ? false
          : Boolean(Number(values.extractArchiveEnabled)),
      extractKeepArchive:
        values.extractKeepArchive === undefined
          ? true
          : Boolean(Number(values.extractKeepArchive)),
      extractRejectPolicy: values.extractRejectPolicy || 'SKIP_ZERO_FAIL',
      maxSizeMb: bytesToMb(values.maxSizeBytes),
      quotaMaxMb: bytesToMb(values.quotaMaxBytes),
      thumbnailMaxSourceMb: bytesToMb(values.thumbnailMaxSourceBytes),
      extractMaxUncompressedMb: bytesToMb(values.extractMaxUncompressedBytes),
      allowedContentTypes: splitCsv(values.allowedContentTypes),
      allowedExtensions: splitCsv(values.allowedExtensions),
      extractAllowedContentTypes: splitCsv(values.extractAllowedContentTypes),
      extractAllowedExtensions: splitCsv(values.extractAllowedExtensions),
      accessRules,
    };
    },
    [values, isEdit],
  );

  useEffect(() => {
    if (modalVisible) {
      prevExtsRef.current = splitCsv(values.allowedExtensions) || [];
      extractPrevExtsRef.current = splitCsv(values.extractAllowedExtensions) || [];
    }
  }, [modalVisible, values.allowedExtensions, values.extractAllowedExtensions]);

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
        extractMaxUncompressedMb,
        maxSizeBytes: _dropMax,
        quotaMaxBytes: _dropQuota,
        thumbnailMaxSourceBytes: _dropThumbSrc,
        extractMaxUncompressedBytes: _dropExtractSrc,
        accessRules,
        ...rest
      } = formValues;
      if (!formValues.requireAuth && String(formValues.uploadPerm || '').trim()) {
        hide();
        message.error('匿名上传时不能配置上传权限码');
        return false;
      }
      const rules = Array.isArray(accessRules) ? accessRules : [];
      if (formValues.visibility === 'PRIVATE' && formValues.requireAuth && rules.length === 0) {
        hide();
        message.error('私有且要求登录时请至少配置一条访问规则');
        return false;
      }
      if (formValues.extractArchiveEnabled) {
        const exts = (formValues.allowedExtensions || []).map((s: string) => normalizeExt(s));
        const mimes = (formValues.allowedContentTypes || []).map((s: string) => String(s).trim().toLowerCase());
        if (exts.length > 0 && !exts.includes('zip')) {
          hide();
          message.error('开启 zip 自动展开时，外层允许扩展名需包含 zip（或留空不限制）');
          return false;
        }
        if (
          mimes.length > 0
          && !mimes.includes('application/zip')
          && !mimes.includes('application/x-zip-compressed')
        ) {
          hide();
          message.error('开启 zip 自动展开时，外层允许 MIME 需包含 application/zip（或留空不限制）');
          return false;
        }
      }
      if (
        formValues.visibility === 'PRIVATE'
        && rules.length > 0
        && rules.every((rule: any) => !rule?.downloadScope || rule.downloadScope === 'OFF')
      ) {
        hide();
        message.error('私有场景请至少给一行配置下载范围');
        return false;
      }
      const payload = {
        ...rest,
        allowedContentTypes: joinCsv(formValues.allowedContentTypes),
        allowedExtensions: joinCsv(formValues.allowedExtensions),
        extractAllowedContentTypes: joinCsv(formValues.extractAllowedContentTypes),
        extractAllowedExtensions: joinCsv(formValues.extractAllowedExtensions),
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
        extractMaxEntries:
          formValues.extractMaxEntries == null || formValues.extractMaxEntries === ''
            ? 0
            : formValues.extractMaxEntries,
        extractMaxUncompressedBytes: mbToBytes(extractMaxUncompressedMb),
        callerPolicy: buildOssAccessRulesJson(
          formValues.visibility === 'PUBLIC'
            ? rules.map((rule: any) => ({ ...rule, downloadScope: 'OFF' }))
            : rules,
        ),
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
      form={form}
      title={isEdit ? '编辑上传配置' : '新建上传配置'}
      width="min(1440px, 94vw)"
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
        className: 'oss-upload-profile-drawer',
        styles: { body: { padding: '10px 16px 0' } },
      }}
      labelCol={{ style: { width: 118 } }}
      grid={true}
      rowProps={{
        gutter: [12, 0],
      }}
      submitter={{
        searchConfig: {
          submitText: '提交',
          resetText: '取消',
        },
      }}
    >
      {/* ── 1. 基础信息与存储关联 ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="blue" style={{ margin: 0 }}>1</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>📌 基础信息与存储关联</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 6 }}
          styles={{ body: { padding: '6px 12px 0 12px' } }}
        >
          <Row gutter={[12, 0]}>
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
              fieldProps={{
                onChange: (nextVisibility: string) => {
                  if (nextVisibility === 'PUBLIC') {
                    const rules = form.getFieldValue('accessRules') || [];
                    form.setFieldValue(
                      'accessRules',
                      rules.map((rule: any) => ({ ...rule, downloadScope: 'OFF' })),
                    );
                  }
                },
              }}
            />
            <ProFormDependency name={['connectionCode']}>
              {({ connectionCode }) => {
                const bucketOptions: { label: string; value: string }[] = [];
                const added = new Set<string>();

                if (connectionCode) {
                  const selectedConn = rawConnections.find((c) => c.code === connectionCode);
                  if (selectedConn?.publicBucket?.trim()) {
                    const val = selectedConn.publicBucket.trim();
                    added.add(val);
                    bucketOptions.push({ label: `${val}（公有桶）`, value: val });
                  }
                  if (selectedConn?.privateBucket?.trim()) {
                    const val = selectedConn.privateBucket.trim();
                    if (!added.has(val)) {
                      added.add(val);
                      bucketOptions.push({ label: `${val}（私有桶）`, value: val });
                    }
                  }

                  const remoteList = minioBucketsMap[connectionCode];
                  if (Array.isArray(remoteList)) {
                    remoteList.forEach((b) => {
                      if (b?.trim() && !added.has(b.trim())) {
                        const val = b.trim();
                        added.add(val);
                        bucketOptions.push({
                          label: val,
                          value: val,
                        });
                      }
                    });
                  }
                }

                const isLoading = connectionCode ? Boolean(loadingMap[connectionCode]) : false;

                return (
                  <>
                    <BucketLoader connectionCode={connectionCode} onLoad={loadMinioBuckets} />
                    <Col span={12}>
                      <Form.Item
                        name="bucketOverride"
                        label="桶覆盖"
                        tooltip="可选，留空默认使用连接配置的公有/私有桶。下拉可选择 MinIO 实例上的其他存储桶，也可手写输入"
                      >
                        <AutoComplete
                          onDropdownVisibleChange={(open) => {
                            if (open && connectionCode) {
                              loadMinioBuckets(connectionCode);
                            }
                          }}
                          onFocus={() => {
                            if (connectionCode) {
                              loadMinioBuckets(connectionCode);
                            }
                          }}
                          options={bucketOptions}
                          placeholder={
                            isLoading
                              ? '正在获取 MinIO 存储桶列表...'
                              : '可选，选择其他存储桶或手动输入'
                          }
                          allowClear
                          filterOption={(inputValue, option) =>
                            String(option?.value || '').toLowerCase().includes(inputValue.toLowerCase()) ||
                            String(option?.label || '').toLowerCase().includes(inputValue.toLowerCase())
                          }
                        />
                      </Form.Item>
                    </Col>
                  </>
                );
              }}
            </ProFormDependency>
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
          </Row>
        </Card>
      </Col>

      {/* ── 2. 存储路径与命名规则 ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="cyan" style={{ margin: 0 }}>2</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>📁 存储路径与 Key 规则</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 6 }}
          styles={{ body: { padding: '6px 12px 0 12px' } }}
        >
          <Row gutter={[12, 0]}>
            <ProFormText
              name="keyPattern"
              label="Key 模式"
              placeholder="{profile}/{yyyy}/{MM}/{uuid}_{filename}"
              colProps={{ span: 24 }}
              tooltip="对象键模板；连接上的 Key 前缀会再拼在最前面。占位符：{profile} 场景编码、{yyyy}/{MM}/{dd} 年月日、{HH}/{mm}/{ss} 时分秒、{uuid}、{filename} 完整文件名、{name} 不含扩展名、{ext} 扩展名(小写无点)。默认 {profile}/{yyyy}/{MM}/{uuid}_{filename}"
              extra={
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
                  默认 <code>{'{profile}/{yyyy}/{MM}/{uuid}_{filename}'}</code>，占位符见问号
                </span>
              }
            />
          </Row>
        </Card>
      </Col>

      {/* ── 3. 上传与访问权限（策略前置） ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="purple" style={{ margin: 0 }}>3</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>🔐 上传与访问权限</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 6 }}
          styles={{ body: { padding: '6px 12px 0 12px' } }}
        >
          <Row gutter={[12, 0]}>
            <ProFormSwitch
              name="requireAuth"
              label="上传要求登录"
              tooltip="开启后，调用上传 API 必须携带登录凭证；关闭后允许游客匿名上传。"
              colProps={{ span: 12 }}
              fieldProps={{
                onChange: (checked: boolean) => {
                  if (!checked && form.getFieldValue('uploadPerm')) {
                    form.setFieldValue('uploadPerm', '');
                  }
                },
              }}
            />
            <ProFormSwitch
              name="presignUploadEnabled"
              label="开放预签名直传"
              tooltip="开启后可调用 /oss/presign/init 换取预签名 PUT 地址，由客户端直传 OSS（不经过网关，不受 multipart 50MB 限制），适合大文件；仍受本场景单文件上限与扩展名/MIME 白名单约束。需在桶上放通跨域 PUT。"
              colProps={{ span: 12 }}
            />
            <ProFormDependency name={['visibility', 'requireAuth']}>
              {({ visibility, requireAuth }) => (
                <OssAccessRulesFields visibility={visibility} requireAuth={requireAuth} />
              )}
            </ProFormDependency>
            <Col span={24}>
              <Collapse
                ghost
                size="small"
                items={[
                  {
                    key: 'console',
                    label: '高级：Flow 管理端权限码（宿主用户看不到这层）',
                    children: (
                      <Row gutter={[12, 0]}>
                        <ProFormDependency name={['visibility', 'requireAuth']}>
                          {({ visibility, requireAuth }) => (
                            <ProFormText
                              name="uploadPerm"
                              label="上传权限码"
                              placeholder="可选，如 flow:oss:upload；留空=不校验 Flow 权限"
                              colProps={{ span: visibility === 'PRIVATE' ? 12 : 24 }}
                              disabled={!requireAuth}
                              tooltip={
                                requireAuth
                                  ? '仅约束带着 Flow JWT 的管理端账号。宿主用户只走上面的访问规则。'
                                  : '匿名上传没有可校验的 Flow 用户身份，不能配置权限码'
                              }
                            />
                          )}
                        </ProFormDependency>
                        <ProFormDependency name={['visibility']}>
                          {({ visibility }) =>
                            visibility === 'PRIVATE' ? (
                              <ProFormText
                                name="downloadPerm"
                                label="下载权限码"
                                placeholder="可选，如 flow:oss:download:all"
                                colProps={{ span: 12 }}
                                tooltip="访问规则不通过时，拥有此权限码的 Flow 管理端用户可跨范围查看/下载；宿主用户无效"
                              />
                            ) : null
                          }
                        </ProFormDependency>
                      </Row>
                    ),
                  },
                ]}
              />
            </Col>
          </Row>
        </Card>
      </Col>

      {/* ── 4. 格式校验、容量与缩略图 ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="green" style={{ margin: 0 }}>4</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>🛡️ 格式校验、缩略图与压缩包</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 6 }}
          styles={{ body: { padding: '6px 12px 0 12px' } }}
        >
          <Row gutter={[12, 0]}>
            <Col span={24}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  background: '#f8fafc',
                  padding: '5px 10px',
                  borderRadius: 4,
                  border: '1px dashed #cbd5e1',
                  marginBottom: 8,
                }}
              >
                <span style={{ fontSize: 12, fontWeight: 500, color: '#475569', whiteSpace: 'nowrap' }}>
                  ⚡ 常用场景一键预填规则：
                </span>
                <Space wrap size={[4, 4]}>
                  {PRESET_SCENARIOS.map((p) => (
                    <Button
                      key={p.key}
                      size="small"
                      type="dashed"
                      onClick={() => {
                        const curMimes: string[] = form.getFieldValue('allowedContentTypes') || [];
                        const curExts: string[] = form.getFieldValue('allowedExtensions') || [];
                        const newMimes = Array.from(new Set([...curMimes, ...p.mimeTypes]));
                        const newExts = Array.from(new Set([...curExts, ...p.exts]));
                        form.setFieldsValue({
                          allowedContentTypes: newMimes,
                          allowedExtensions: newExts,
                        });
                        prevExtsRef.current = newExts;
                        message.success(`已追加「${p.label}」格式校验规则`);
                      }}
                    >
                      {p.label}
                    </Button>
                  ))}
                  <Button
                    size="small"
                    danger
                    type="text"
                    onClick={() => {
                      form.setFieldsValue({
                        allowedContentTypes: [],
                        allowedExtensions: [],
                      });
                      prevExtsRef.current = [];
                      message.info('已清空格式限制');
                    }}
                  >
                    🧹 清空限制
                  </Button>
                </Space>
              </div>
            </Col>
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
                onChange: (exts: string[]) => {
                  const nextExts = uniqueStrings((exts || []).map(normalizeExt));
                  const currentMimes: string[] = form.getFieldValue('allowedContentTypes') || [];
                  const nextMimes = mergeMimesFromExts(currentMimes, prevExtsRef.current, nextExts);
                  prevExtsRef.current = nextExts;
                  form.setFieldsValue({
                    allowedExtensions: nextExts,
                    allowedContentTypes: nextMimes,
                  });
                },
              }}
              tooltip="不含点，如 png；空=不限制。选中后自动带出对应 MIME，可再手改"
            />
            <ProFormSelect
              name="allowedContentTypes"
              label="允许 MIME"
              mode="tags"
              options={MIME_OPTIONS}
              placeholder="由扩展名带出，也可再选或手输"
              colProps={{ span: 12 }}
              fieldProps={{
                tokenSeparators: [',', '，', ' '],
                maxTagCount: 'responsive',
              }}
              tooltip="空=不限制。选扩展名会自动带出；也可单独增删，提交时按逗号写入后端"
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
            <ProFormSwitch
              name="thumbnailEnabled"
              label="生成缩略图"
              tooltip="按本业务场景开启；边长/质量可单独配置，未填则用全局默认"
              colProps={{ span: 24 }}
            />
            <ProFormDependency name={['thumbnailEnabled']}>
              {({ thumbnailEnabled }) =>
                thumbnailEnabled ? (
                  <Col span={24}>
                    <div
                      style={{
                        background: '#fcfaff',
                        border: '1px solid #f3e8ff',
                        borderRadius: 6,
                        padding: '8px 12px 0 12px',
                        marginBottom: 8,
                      }}
                    >
                      <Row gutter={[12, 0]}>
                        <ProFormDigit
                          name="thumbnailMaxEdge"
                          label="缩略图最长边"
                          placeholder="空=全局 256"
                          min={16}
                          max={4096}
                          fieldProps={{ precision: 0 }}
                          colProps={{ span: 8 }}
                          tooltip="像素；头像场景可设 128，相册可设 512"
                        />
                        <ProFormDigit
                          name="thumbnailMaxSourceMb"
                          label="源文件上限"
                          placeholder="空=全局默认"
                          min={0}
                          fieldProps={{ precision: 3, addonAfter: 'MB' }}
                          colProps={{ span: 8 }}
                          tooltip="超过则跳过生成"
                        />
                        <ProFormDigit
                          name="thumbnailJpegQuality"
                          label="JPEG 质量"
                          placeholder="空=全局 0.85"
                          min={0.1}
                          max={1}
                          fieldProps={{ step: 0.05, precision: 2 }}
                          colProps={{ span: 8 }}
                        />
                      </Row>
                    </div>
                  </Col>
                ) : null
              }
            </ProFormDependency>
            <ProFormSwitch
              name="extractArchiveEnabled"
              label="上传 zip 后自动展开"
              tooltip="仅处理 zip，不递归解压。外层白名单管能否上传这个包，下面内层白名单管包内哪些文件落库"
              colProps={{ span: 24 }}
              fieldProps={{
                onChange: (checked: boolean) => {
                  if (!checked) return;
                  const curExts: string[] = form.getFieldValue('allowedExtensions') || [];
                  if (!curExts.length) return;
                  const hasZip = curExts.map(normalizeExt).includes('zip');
                  if (hasZip) return;
                  const nextExts = uniqueStrings([...curExts.map(normalizeExt), 'zip']);
                  const curMimes: string[] = form.getFieldValue('allowedContentTypes') || [];
                  const nextMimes = uniqueStrings([...curMimes, 'application/zip']);
                  form.setFieldsValue({
                    allowedExtensions: nextExts,
                    allowedContentTypes: nextMimes,
                  });
                  prevExtsRef.current = nextExts;
                  message.info('已在外层白名单追加 zip，否则无法上传压缩包');
                },
              }}
            />
            <ProFormDependency name={['extractArchiveEnabled']}>
              {({ extractArchiveEnabled }) =>
                extractArchiveEnabled ? (
                  <Col span={24}>
                    <div
                      style={{
                        background: '#f0f9ff',
                        border: '1px solid #bae6fd',
                        borderRadius: 6,
                        padding: '8px 12px 0 12px',
                        marginBottom: 8,
                      }}
                    >
                      <div style={{ fontSize: 12, color: '#0369a1', marginBottom: 6 }}>
                        外层管「包能不能上传」，内层管「包内哪些文件落库」。空内层=除嵌套压缩包与系统垃圾文件外全部落库。不支持 rar/7z，也不二次解压。
                      </div>
                      <Row gutter={[12, 0]}>
                        <ProFormSwitch
                          name="extractKeepArchive"
                          label="保留原 zip"
                          tooltip="关闭后，展开成功且至少落库 1 个文件时软删原包；失败则始终保留原包便于重试"
                          colProps={{ span: 12 }}
                        />
                        <ProFormSelect
                          name="extractRejectPolicy"
                          label="不合格条目"
                          colProps={{ span: 12 }}
                          options={[
                            { label: '跳过；合格数为 0 则整包失败', value: 'SKIP_ZERO_FAIL' },
                            { label: '任一不合格则整包失败并回滚', value: 'FAIL_PACK' },
                          ]}
                          tooltip="目录、__MACOSX、.DS_Store、嵌套压缩包始终跳过，不计入不合格"
                        />
                        <Col span={24}>
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                              marginBottom: 4,
                            }}
                          >
                            <span style={{ fontSize: 12, color: '#475569' }}>包内落库一键预填：</span>
                            <Space wrap size={[4, 4]}>
                              {PRESET_SCENARIOS.filter((p) => p.key === 'IMAGE' || p.key === 'DOCUMENT').map((p) => (
                                <Button
                                  key={`inner-${p.key}`}
                                  size="small"
                                  type="dashed"
                                  onClick={() => {
                                    const curMimes: string[] = form.getFieldValue('extractAllowedContentTypes') || [];
                                    const curExts: string[] = form.getFieldValue('extractAllowedExtensions') || [];
                                    const newMimes = uniqueStrings([...curMimes, ...p.mimeTypes]);
                                    const newExts = uniqueStrings([...curExts, ...p.exts]);
                                    form.setFieldsValue({
                                      extractAllowedContentTypes: newMimes,
                                      extractAllowedExtensions: newExts,
                                    });
                                    extractPrevExtsRef.current = newExts;
                                    message.success(`已追加包内「${p.label}」`);
                                  }}
                                >
                                  {p.label}
                                </Button>
                              ))}
                              <Button
                                size="small"
                                danger
                                type="text"
                                onClick={() => {
                                  form.setFieldsValue({
                                    extractAllowedContentTypes: [],
                                    extractAllowedExtensions: [],
                                  });
                                  extractPrevExtsRef.current = [];
                                  message.info('已清空包内限制');
                                }}
                              >
                                清空包内限制
                              </Button>
                            </Space>
                          </div>
                        </Col>
                        <ProFormSelect
                          name="extractAllowedExtensions"
                          label="包内扩展名"
                          mode="tags"
                          options={EXT_OPTIONS.filter((o) => !['zip', 'rar', '7z'].includes(o.value))}
                          placeholder="空=不限制（仍排除嵌套压缩包）"
                          colProps={{ span: 12 }}
                          fieldProps={{
                            tokenSeparators: [',', '，', ' '],
                            maxTagCount: 'responsive',
                            onChange: (exts: string[]) => {
                              const nextExts = uniqueStrings((exts || []).map(normalizeExt));
                              const currentMimes: string[] = form.getFieldValue('extractAllowedContentTypes') || [];
                              const nextMimes = mergeMimesFromExts(
                                currentMimes,
                                extractPrevExtsRef.current,
                                nextExts,
                              );
                              extractPrevExtsRef.current = nextExts;
                              form.setFieldsValue({
                                extractAllowedExtensions: nextExts,
                                extractAllowedContentTypes: nextMimes,
                              });
                            },
                          }}
                          tooltip="按扩展名 + 文件头魔数校验，不信任压缩包内自带类型"
                        />
                        <ProFormSelect
                          name="extractAllowedContentTypes"
                          label="包内 MIME"
                          mode="tags"
                          options={MIME_OPTIONS.filter((o) => !String(o.value).includes('zip') && !String(o.value).includes('rar') && !String(o.value).includes('7z'))}
                          placeholder="由扩展名带出，也可再选"
                          colProps={{ span: 12 }}
                          fieldProps={{
                            tokenSeparators: [',', '，', ' '],
                            maxTagCount: 'responsive',
                          }}
                        />
                        <ProFormDigit
                          name="extractMaxEntries"
                          label="单包最多条目"
                          placeholder="空=全局 200"
                          min={1}
                          max={5000}
                          fieldProps={{ precision: 0 }}
                          colProps={{ span: 12 }}
                          tooltip="含将被跳过的文件条目，用于抑制恶意包"
                        />
                        <ProFormDigit
                          name="extractMaxUncompressedMb"
                          label="解压后体积上限"
                          placeholder="空=全局 512MB"
                          min={0}
                          fieldProps={{ precision: 0, addonAfter: 'MB' }}
                          colProps={{ span: 12 }}
                          tooltip="按解压后累计字节限制，防止 zip bomb"
                        />
                      </Row>
                    </div>
                  </Col>
                ) : null
              }
            </ProFormDependency>
          </Row>
        </Card>
      </Col>

      {/* ── 5. 业务定义与备注 ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="orange" style={{ margin: 0 }}>5</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>🧩 业务定义与备注</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 6 }}
          styles={{ body: { padding: '6px 12px 0 12px' } }}
        >
          <Row gutter={[12, 0]}>
            <ProFormTextArea
              name="bizFieldsSchema"
              label="业务字段 Schema"
              placeholder='可选 JSON 数组/对象，如 [{"name":"orderId","required":true}]'
              fieldProps={{ rows: 2 }}
              colProps={{ span: 24 }}
            />
            <ProFormTextArea
              name="remark"
              label="备注"
              placeholder="可选说明信息"
              fieldProps={{ rows: 2 }}
              colProps={{ span: 24 }}
            />
          </Row>
        </Card>
      </Col>
    </DrawerForm>
  );
};

export default OssUploadProfileForm;

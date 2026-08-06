import {
  DrawerForm,
  ProFormDependency,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { AutoComplete, Button, Card, Col, Form, Row, Space, Tag, message } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';

import { queryOssConnectionOptions, queryOssConnectionBuckets, OssConnection } from '@/services/flow/ossConnection';
import {
  createOssUploadProfile,
  OssUploadProfile,
  updateOssUploadProfile,
} from '@/services/flow/ossUploadProfile';

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
      fetchedCodesRef.current.clear();
      return;
    }
    (async () => {
      try {
        const list = await queryOssConnectionOptions();
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
        setRawConnections([]);
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
      form={form}
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
        gutter: [16, 4],
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
          style={{ borderRadius: 6, marginBottom: 8 }}
          styles={{ body: { padding: '8px 14px 0 14px' } }}
        >
          <Row gutter={[16, 0]}>
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
            <ProFormDependency name={['connectionCode']}>
              {({ connectionCode }) => {
                const bucketOptions: { label: string; value: string }[] = [];
                const added = new Set<string>();

                if (connectionCode) {
                  const selectedConn = rawConnections.find((c) => c.code === connectionCode);
                  if (selectedConn?.publicBucket?.trim()) {
                    added.add(selectedConn.publicBucket.trim());
                  }
                  if (selectedConn?.privateBucket?.trim()) {
                    added.add(selectedConn.privateBucket.trim());
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
          style={{ borderRadius: 6, marginBottom: 8 }}
          styles={{ body: { padding: '8px 14px 0 14px' } }}
        >
          <Row gutter={[16, 0]}>
            <ProFormText
              name="keyPattern"
              label="Key 模式"
              placeholder="{profile}/{yyyy}/{MM}/{uuid}_{filename}"
              colProps={{ span: 24 }}
              tooltip="对象键模板；连接上的 Key 前缀会再拼在最前面"
              extra={
                <span style={{ fontSize: 12, color: '#8c8c8c' }}>
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
          </Row>
        </Card>
      </Col>

      {/* ── 3. 上传限制与格式校验 ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="green" style={{ margin: 0 }}>3</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>🛡️ 上传限制与格式校验</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 8 }}
          styles={{ body: { padding: '8px 14px 0 14px' } }}
        >
          <Row gutter={[16, 0]}>
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
                      message.info('已清空格式限制');
                    }}
                  >
                    🧹 清空限制
                  </Button>
                </Space>
              </div>
            </Col>
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
          </Row>
        </Card>
      </Col>

      {/* ── 4. 访问权限与缩略图策略 ── */}
      <Col span={24}>
        <Card
          size="small"
          title={
            <Space size={6}>
              <Tag color="purple" style={{ margin: 0 }}>4</Tag>
              <span style={{ fontWeight: 600, fontSize: 13 }}>🖼️ 访问权限与缩略图策略</span>
            </Space>
          }
          bordered
          style={{ borderRadius: 6, marginBottom: 8 }}
          styles={{ body: { padding: '8px 14px 0 14px' } }}
        >
          <Row gutter={[16, 0]}>
            <ProFormSwitch
              name="requireAuth"
              label="上传要求登录"
              tooltip="开启后，调用上传 API 必须携带登录凭证；关闭后允许游客匿名上传。私有文件的下载鉴权由可见性 (PRIVATE) 及数据范围决定。"
              colProps={{ span: 24 }}
            />
            <ProFormDependency name={['visibility']}>
              {({ visibility }) => (
                <ProFormText
                  name="uploadPerm"
                  label="上传权限码"
                  placeholder="可选，如 flow:oss:upload；留空=仅要求登录控制"
                  colProps={{ span: visibility === 'PRIVATE' ? 12 : 12 }}
                  tooltip="填写后，拥有该 RBAC 权限码的用户才能上传文件；多个权限码以英文逗号分隔"
                />
              )}
            </ProFormDependency>
            <ProFormDependency name={['visibility']}>
              {({ visibility }) =>
                visibility === 'PRIVATE' ? (
                  <ProFormText
                    name="downloadPerm"
                    label="下载权限码"
                    placeholder="可选，如 flow:oss:download:all；留空=仅 DataScope 控制"
                    colProps={{ span: 12 }}
                    tooltip="私有文件下载：DataScope 不通过时，拥有此权限码的用户可跨范围访问（适合管理员/客服角色）；留空=不开启权限码兜底"
                  />
                ) : null
              }
            </ProFormDependency>
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
                      <Row gutter={[16, 0]}>
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
          style={{ borderRadius: 6, marginBottom: 8 }}
          styles={{ body: { padding: '8px 14px 0 14px' } }}
        >
          <Row gutter={[16, 0]}>
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

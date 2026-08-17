import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Modal,
  Segmented,
  Space,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import {
  CloudUploadOutlined,
  CopyOutlined,
  FileOutlined,
  CheckCircleFilled,
  RightOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import { history } from '@umijs/max';

import { OssUploadProfile } from '@/services/flow/ossUploadProfile';
import {
  OssUploadResult,
  uploadOssByPresign,
  uploadOssByProfile,
} from '@/services/flow/ossObject';

export type OssSimulateUploadModalProps = {
  open: boolean;
  profile?: Partial<OssUploadProfile> | null;
  onClose: () => void;
};

function formatBytes(bytes?: number): string {
  if (bytes == null || isNaN(bytes) || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

/**
 * 管理端试上传：走真实 POST /flow-api/oss/upload，结果可进 OSS 文件台账核对。
 */
const OssSimulateUploadModal: React.FC<OssSimulateUploadModalProps> = ({
  open,
  profile,
  onClose,
}) => {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [results, setResults] = useState<OssUploadResult[]>([]);
  const [uploadMode, setUploadMode] = useState<'STANDARD' | 'PRESIGN'>('STANDARD');

  useEffect(() => {
    if (open) {
      setUploadMode(profile?.presignUploadEnabled ? 'PRESIGN' : 'STANDARD');
    }
  }, [open, profile?.presignUploadEnabled]);

  const reset = () => {
    setFileList([]);
    setResults([]);
    setUploading(false);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const maxFiles =
    profile?.maxFilesPerRequest && profile.maxFilesPerRequest > 0
      ? profile.maxFilesPerRequest
      : 10;
  const isMultiple = maxFiles > 1;

  const handleOk = async () => {
    const rawFiles = fileList
      .map((f) => f.originFileObj)
      .filter((f): f is NonNullable<typeof f> => Boolean(f));
    if (!profile?.code) {
      message.warning('场景编码缺失');
      return;
    }
    if (rawFiles.length === 0) {
      message.warning('请先选择文件');
      return;
    }
    if (rawFiles.length > maxFiles) {
      message.warning(`当前场景单次最多允许上传 ${maxFiles} 个文件`);
      return;
    }
    setUploading(true);
    try {
      const list =
        uploadMode === 'PRESIGN'
          ? await Promise.all(rawFiles.map((file) => uploadOssByPresign(profile.code!, file)))
          : await uploadOssByProfile(profile.code, rawFiles);
      const newItems = Array.isArray(list) ? list : [];
      setResults((prev) => [...newItems, ...prev]);
      setFileList([]); // 成功后自动清空待上传列表，防止二次重复提交
      message.success(`成功上传 ${newItems.length} 个文件`);
    } catch (e: any) {
      if (!e?.message?.includes('DEMO_RESTRICTED')) {
        message.error(e?.message || '上传失败');
      }
    } finally {
      setUploading(false);
    }
  };

  return (
    <Modal
      title={
        <Space size={8}>
          <CloudUploadOutlined style={{ color: '#1677ff', fontSize: 18 }} />
          <span>试上传测试 — {profile?.name || profile?.code || ''}</span>
        </Space>
      }
      open={open}
      onCancel={() => {
        if (!uploading) handleClose();
      }}
      onOk={handleOk}
      okText="开始上传"
      confirmLoading={uploading}
      closable={!uploading}
      maskClosable={!uploading}
      keyboard={!uploading}
      destroyOnClose
      width={680}
      styles={{ body: { padding: '16px 24px' } }}
    >
      {/* ── 1. 场景配置感知 Banner ── */}
      <div
        style={{
          background: 'linear-gradient(135deg, #e6f4ff 0%, #f0f5ff 100%)',
          border: '1px solid #bae0ff',
          borderRadius: 8,
          padding: '10px 14px',
          marginBottom: 16,
        }}
      >
        <div style={{ fontWeight: 600, fontSize: 13, color: '#0958d9', marginBottom: 4 }}>
          ⚙️ 场景配置规则生效中（编码：{profile?.code}）
        </div>
        <Space size={[8, 4]} wrap style={{ fontSize: 12, color: '#595959' }}>
          <span>单次上限：<b>{maxFiles} 个文件</b></span>
          <span>·</span>
          <span>可见性：<Tag color={profile?.visibility === 'PUBLIC' ? 'green' : 'purple'} style={{ margin: 0 }}>{profile?.visibility || 'PRIVATE'}</Tag></span>
          <span>·</span>
          <span>要求登录：<b>{profile?.requireAuth !== false ? '是' : '否'}</b></span>
          {profile?.maxSizeBytes ? (
            <>
              <span>·</span>
              <span>单文件上限：<b>{formatBytes(profile.maxSizeBytes)}</b></span>
            </>
          ) : null}
        </Space>
      </div>

      {profile?.presignUploadEnabled ? (
        <div style={{ marginBottom: 14 }}>
          <Segmented
            block
            value={uploadMode}
            disabled={uploading}
            onChange={(value) => setUploadMode(value as 'STANDARD' | 'PRESIGN')}
            options={[
              { label: '普通上传（经过网关）', value: 'STANDARD' },
              { label: '预签名直传（客户端直连 OSS）', value: 'PRESIGN' },
            ]}
          />
        </div>
      ) : null}

      {/* ── 2. 拖拽/点击上传选区 ── */}
      <Upload.Dragger
        maxCount={maxFiles}
        multiple={isMultiple}
        disabled={uploading}
        fileList={fileList}
        beforeUpload={() => false}
        onChange={({ fileList: next }) => {
          setFileList(next);
          setResults([]);
        }}
        style={{
          borderRadius: 8,
          background: '#fafafa',
          border: '1.5px dashed #d9d9d9',
          padding: '16px 0',
        }}
      >
        <p className="ant-upload-drag-icon" style={{ marginBottom: 8 }}>
          <CloudUploadOutlined style={{ fontSize: 32, color: '#1677ff' }} />
        </p>
        <p className="ant-upload-text" style={{ fontSize: 14, fontWeight: 500 }}>
          点击或将文件拖拽到此处 {isMultiple ? `(最多可选 ${maxFiles} 个)` : ''}
        </p>
        <p className="ant-upload-hint" style={{ fontSize: 12, color: '#8c8c8c' }}>
          按场景规则校验 MIME 类型与扩展名，上传后将生成真实 OSS 存储对象与数据库台账
        </p>
      </Upload.Dragger>

      {/* ── 3. 成功生成台账结果区 ── */}
      {results.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 10,
            }}
          >
            <Space size={6}>
              <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />
              <span style={{ fontWeight: 600, fontSize: 14, color: '#262626' }}>
                成功生成 {results.length} 个文件台账
              </span>
            </Space>
            <Space size={4}>
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => setResults([])}
              >
                清空结果
              </Button>
              <Button
                type="link"
                size="small"
                icon={<RightOutlined />}
                onClick={() => {
                  handleClose();
                  history.push('/flow/oss-object');
                }}
              >
                在文件台账中查看全部
              </Button>
            </Space>
          </div>

          <div style={{ maxHeight: 260, overflowY: 'auto', paddingRight: 4 }}>
            {results.map((r) => (
              <Card
                key={r.id}
                size="small"
                style={{
                  marginBottom: 10,
                  borderRadius: 6,
                  borderColor: '#b7eb8f',
                  background: '#f6ffed',
                }}
                styles={{ body: { padding: '10px 14px' } }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                      <FileOutlined style={{ color: '#52c41a', fontSize: 15 }} />
                      <span
                        style={{
                          fontWeight: 600,
                          fontSize: 13,
                          color: '#1f1f1f',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {r.originalName}
                      </span>
                      <Tag color={r.visibility === 'PUBLIC' ? 'green' : 'purple'} style={{ fontSize: 11, margin: 0 }}>
                        {r.visibility}
                      </Tag>
                    </div>

                    <Space size={12} wrap style={{ fontSize: 12, color: '#595959' }}>
                      <span>大小：<b>{formatBytes(r.sizeBytes)}</b></span>
                      <span>
                        台账 ID：
                        <Typography.Text copyable={{ text: r.id }} style={{ fontSize: 12 }}>
                          <code>{r.id}</code>
                        </Typography.Text>
                      </span>
                      {r.thumbStatus ? (
                        <span>
                          缩略图：
                          <Tag
                            color={r.thumbStatus === 'SUCCESS' ? 'blue' : 'default'}
                            style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', margin: 0 }}
                          >
                            {r.thumbStatus}
                          </Tag>
                        </span>
                      ) : null}
                    </Space>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}
    </Modal>
  );
};

export default OssSimulateUploadModal;

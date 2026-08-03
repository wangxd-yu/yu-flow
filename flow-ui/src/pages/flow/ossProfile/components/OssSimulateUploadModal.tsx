import { Alert, Modal, Typography, Upload, message } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import React, { useState } from 'react';
import { history } from '@umijs/max';

import { OssUploadProfile } from '@/services/flow/ossUploadProfile';
import { OssUploadResult, uploadOssByProfile } from '@/services/flow/ossObject';

export type OssSimulateUploadModalProps = {
  open: boolean;
  profile?: Partial<OssUploadProfile> | null;
  onClose: () => void;
};

/**
 * 管理端试上传：走真实 POST /flow-api/oss/upload，结果可进 OSS 文件核对。
 */
const OssSimulateUploadModal: React.FC<OssSimulateUploadModalProps> = ({
  open,
  profile,
  onClose,
}) => {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [results, setResults] = useState<OssUploadResult[]>([]);

  const reset = () => {
    setFileList([]);
    setResults([]);
    setUploading(false);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleOk = async () => {
    const file = fileList[0]?.originFileObj as File | undefined;
    if (!profile?.code) {
      message.warning('场景编码缺失');
      return;
    }
    if (!file) {
      message.warning('请先选择文件');
      return;
    }
    setUploading(true);
    try {
      const list = await uploadOssByProfile(profile.code, file);
      setResults(Array.isArray(list) ? list : []);
      message.success('上传成功，可在 OSS 文件查看');
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
      title={`试上传 — ${profile?.name || profile?.code || ''}`}
      open={open}
      onCancel={handleClose}
      onOk={handleOk}
      okText="开始上传"
      confirmLoading={uploading}
      destroyOnClose
      width={560}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={`将按场景「${profile?.code}」走真实上传链路（连接 / 桶 / 类型限制 / 缩略图均生效）`}
      />
      <Upload
        maxCount={1}
        fileList={fileList}
        beforeUpload={() => false}
        onChange={({ fileList: next }) => {
          setFileList(next);
          setResults([]);
        }}
      >
        <a>选择文件</a>
      </Upload>
      {results.length > 0 && (
        <div style={{ marginTop: 16 }}>
          {results.map((r) => (
            <Alert
              key={r.id}
              type="success"
              showIcon
              style={{ marginBottom: 8 }}
              message={
                <Typography.Text>
                  台账 ID：<Typography.Text copyable>{r.id}</Typography.Text>
                </Typography.Text>
              }
              description={
                <div>
                  <div>
                    {r.originalName} · {r.sizeBytes ?? 0} 字节 · {r.visibility}
                  </div>
                  {r.publicPath ? <div>公有路径：{r.publicPath}</div> : null}
                  {r.thumbStatus ? <div>缩略图：{r.thumbStatus}</div> : null}
                  <a
                    style={{ marginTop: 4, display: 'inline-block' }}
                    onClick={() => {
                      handleClose();
                      history.push('/flow/oss-object');
                    }}
                  >
                    打开 OSS 文件
                  </a>
                </div>
              }
            />
          ))}
        </div>
      )}
    </Modal>
  );
};

export default OssSimulateUploadModal;

import React, { useEffect, useState } from 'react';
import { Image, Spin, Tooltip } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import {
  fetchOssObjectThumbnailBlobUrl,
  fetchOssObjectOriginalBlobUrl,
} from '@/services/flow/ossObject';

type ThumbCellProps = {
  objectId: string;
  thumbStatus?: string;
  alt?: string;
};

const ThumbCell: React.FC<ThumbCellProps> = ({ objectId, thumbStatus, alt }) => {
  const [thumbUrl, setThumbUrl] = useState<string>();
  const [originalBlobUrl, setOriginalBlobUrl] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [previewing, setPreviewing] = useState(false);

  // 1. 仅在组件挂载时加载表格列所需的 48px 轻量缩略图
  useEffect(() => {
    if (thumbStatus !== 'READY' || !objectId) {
      setThumbUrl(undefined);
      setFailed(false);
      return;
    }
    let revokedThumb: string | undefined;

    setLoading(true);
    setFailed(false);

    fetchOssObjectThumbnailBlobUrl(objectId)
      .then((url) => {
        revokedThumb = url;
        setThumbUrl(url);
      })
      .catch(() => {
        setFailed(true);
      })
      .finally(() => setLoading(false));

    return () => {
      if (revokedThumb) URL.revokeObjectURL(revokedThumb);
    };
  }, [objectId, thumbStatus]);

  // 2. 当用户点击弹窗展开放大预览时，按需懒加载同源高精度原图 Blob (符合 CSP img-src 'self' data: blob:)
  useEffect(() => {
    if (!previewing || !objectId) {
      return;
    }
    let revokedOrig: string | undefined;
    let isActive = true;

    fetchOssObjectOriginalBlobUrl(objectId)
      .then((url) => {
        if (!isActive) {
          URL.revokeObjectURL(url);
          return;
        }
        revokedOrig = url;
        setOriginalBlobUrl(url);
      })
      .catch(() => {
        if (isActive) {
          /* 降级使用缩略图 */
          setOriginalBlobUrl(thumbUrl);
        }
      });

    return () => {
      isActive = false;
      if (revokedOrig) {
        URL.revokeObjectURL(revokedOrig);
      }
    };
  }, [previewing, objectId, thumbUrl]);

  if (thumbStatus === 'PENDING') {
    return (
      <Tooltip title="缩略图生成中">
        <Spin size="small" />
      </Tooltip>
    );
  }
  if (thumbStatus !== 'READY') {
    return <span style={{ color: '#999' }}>-</span>;
  }
  if (loading) {
    return <Spin size="small" />;
  }
  if (failed || !thumbUrl) {
    return <span style={{ color: '#999' }}>-</span>;
  }

  return (
    <Image
      src={thumbUrl}
      alt={alt || '缩略图'}
      width={48}
      height={48}
      style={{ objectFit: 'cover', borderRadius: 4 }}
      preview={{
        src: originalBlobUrl || thumbUrl,
        mask: (
          <div style={{ fontSize: 11, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
            <EyeOutlined style={{ fontSize: 13 }} />
            <span style={{ lineHeight: 1 }}>预览</span>
          </div>
        ),
        onVisibleChange: (visible) => {
          if (visible) {
            setPreviewing(true);
          }
        },
      }}
    />
  );
};

export default ThumbCell;

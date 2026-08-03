import React, { useEffect, useState } from 'react';
import { Image, Spin, Tooltip } from 'antd';
import { fetchOssObjectThumbnailBlobUrl } from '@/services/flow/ossObject';

type ThumbCellProps = {
  objectId: string;
  thumbStatus?: string;
  alt?: string;
};

const ThumbCell: React.FC<ThumbCellProps> = ({ objectId, thumbStatus, alt }) => {
  const [url, setUrl] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (thumbStatus !== 'READY' || !objectId) {
      setUrl(undefined);
      setFailed(false);
      return;
    }
    let revoked: string | undefined;
    setLoading(true);
    setFailed(false);
    fetchOssObjectThumbnailBlobUrl(objectId)
      .then((blobUrl) => {
        revoked = blobUrl;
        setUrl(blobUrl);
      })
      .catch(() => {
        setFailed(true);
        setUrl(undefined);
      })
      .finally(() => setLoading(false));
    return () => {
      if (revoked) {
        URL.revokeObjectURL(revoked);
      }
    };
  }, [objectId, thumbStatus]);

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
  if (failed || !url) {
    return <span style={{ color: '#999' }}>-</span>;
  }
  return (
    <Image
      src={url}
      alt={alt || '缩略图'}
      width={48}
      height={48}
      style={{ objectFit: 'cover', borderRadius: 4 }}
      preview={{ mask: '预览' }}
    />
  );
};

export default ThumbCell;

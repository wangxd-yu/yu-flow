import React from 'react';
import { Badge, Tag } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  MinusCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';

export type LogStatusKind = 'success' | 'error' | 'processing' | 'skipped';

export interface LogStatusTagProps {
  kind: LogStatusKind;
  /** 展示文案，如「执行成功」「调用成功」「登录失败」 */
  text: string;
}

const LogStatusTag: React.FC<LogStatusTagProps> = ({ kind, text }) => {
  if (kind === 'success') {
    return (
      <Badge
        status="success"
        text={
          <Tag icon={<CheckCircleOutlined />} color="success" style={{ marginInlineEnd: 0 }}>
            {text}
          </Tag>
        }
      />
    );
  }
  if (kind === 'error') {
    return (
      <Badge
        status="error"
        text={
          <Tag icon={<CloseCircleOutlined />} color="error" style={{ marginInlineEnd: 0 }}>
            {text}
          </Tag>
        }
      />
    );
  }
  if (kind === 'processing') {
    return (
      <Badge
        status="processing"
        text={
          <Tag icon={<SyncOutlined spin />} color="processing" style={{ marginInlineEnd: 0 }}>
            {text}
          </Tag>
        }
      />
    );
  }
  return (
    <Badge
      status="default"
      text={
        <Tag icon={<MinusCircleOutlined />} color="default" style={{ marginInlineEnd: 0 }}>
          {text}
        </Tag>
      }
    />
  );
};

export default React.memo(LogStatusTag);

import React from 'react';
import { ClockCircleOutlined } from '@ant-design/icons';
import { formatDuration, getDurationColor } from './logFormat';

export interface LogDurationProps {
  ms?: number | null;
}

/** 列表耗时单元格：时钟图标 + 三档色 */
const LogDuration: React.FC<LogDurationProps> = ({ ms }) => {
  if (ms == null) return <>-</>;
  return (
    <span style={{ color: getDurationColor(ms) }}>
      <ClockCircleOutlined style={{ marginRight: 4 }} />
      {formatDuration(ms)}
    </span>
  );
};

export default React.memo(LogDuration);

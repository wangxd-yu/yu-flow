/**
 * 通用渲染错误边界：包裹重型第三方组件（X6 画布 / Amis Editor / CodeMirror），
 * 崩溃时局部降级为提示卡片，提供「重试渲染」，避免整页白屏丢失工作现场。
 */
import React from 'react';
import { Button, Result, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

interface Props {
  /** 出错时标题里显示的区域名，如「流程编辑器」「页面设计器」 */
  name?: string;
  /** 额外的恢复操作（如「返回列表」） */
  extraActions?: React.ReactNode;
  children: React.ReactNode;
}

interface State {
  error: Error | null;
  /** 递增 key 强制重建子树，实现「重试渲染」 */
  retryKey: number;
}

class ErrorBoundary extends React.Component<Props, State> {
  state: State = { error: null, retryKey: 0 };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // 仅记录，便于排查；不上报外部服务
    console.error(`[ErrorBoundary] ${this.props.name || 'component'} crashed:`, error, info.componentStack);
  }

  handleRetry = () => {
    this.setState((s) => ({ error: null, retryKey: s.retryKey + 1 }));
  };

  render() {
    const { name = '当前区域', extraActions, children } = this.props;
    const { error, retryKey } = this.state;

    if (error) {
      return (
        <Result
          status="error"
          title={`${name}渲染出错`}
          subTitle="已阻止本次崩溃扩散，你的其他工作不受影响；可尝试重试渲染或刷新页面。"
          extra={[
            <Button key="retry" type="primary" icon={<ReloadOutlined />} onClick={this.handleRetry}>
              重试渲染
            </Button>,
            <Button key="reload" onClick={() => window.location.reload()}>
              刷新页面
            </Button>,
            extraActions,
          ]}
        >
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            <Typography.Text code>{String(error?.message || error)}</Typography.Text>
          </Typography.Paragraph>
        </Result>
      );
    }

    return <React.Fragment key={retryKey}>{children}</React.Fragment>;
  }
}

export default ErrorBoundary;

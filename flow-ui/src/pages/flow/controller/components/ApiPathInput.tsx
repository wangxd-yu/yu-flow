/**
 * ApiPathInput
 * ─────────────────────────────────────────────────────────────────────────────
 * 可复用的 API 业务路径输入框（模型 B）：
 * - value = 相对路径（不含目录有效前缀）
 * - addonBefore = SYSTEM_PREFIX + 目录有效前缀
 * - 复制完整 URL = 前缀 + 相对段
 */
import React, { useState, useEffect, useMemo } from 'react';
import { Input, Tooltip } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
import { joinPathSegments, normalizePathPrefix } from '@/utils/apiPathPrefix';

export interface ApiPathInputProps {
  value?: string;
  onChange?: (val: string) => void;
  disabled?: boolean;
  status?: '' | 'error' | 'warning';
  className?: string;
  size?: 'large' | 'middle' | 'small';
  style?: React.CSSProperties;
  /** 目录有效 pathPrefix（根→叶叠加，不含 SYSTEM_PREFIX） */
  directoryPrefix?: string;
  [key: string]: any;
}

const ApiPathInput = React.forwardRef<any, ApiPathInputProps>(
  ({ value, onChange, disabled, status, className, size, style, directoryPrefix, ...rest }, ref) => {
    const [systemPrefix, setSystemPrefix] = useState<string>('/');

    useEffect(() => {
      request('/flow-api/sys-configs/key/SYSTEM_PREFIX', { method: 'GET' })
        .then((res: any) => {
          const data = typeof res === 'string' ? res : res?.data;
          setSystemPrefix(data ? normalizePathPrefix(data) || '/' : '/');
        })
        .catch(() => {
          setSystemPrefix('/');
        });
    }, []);

    const addonBefore = useMemo(() => {
      const joined = joinPathSegments(systemPrefix === '/' ? '' : systemPrefix, directoryPrefix);
      return joined || '/';
    }, [systemPrefix, directoryPrefix]);

    const handleCopy = () => {
      let fullUrl = joinPathSegments(addonBefore === '/' ? '' : addonBefore, value) || '/';
      fullUrl = fullUrl.replace(/(?<!:)\/\/+/g, '/');

      if (navigator.clipboard) {
        navigator.clipboard.writeText(fullUrl).then(() => {
          import('antd').then(({ message }) => message.success('完整 URL 已复制'));
        }).catch(() => {
          import('antd').then(({ message }) => message.error('复制失败，请重试'));
        });
      } else {
        const input = document.createElement('input');
        input.value = fullUrl;
        document.body.appendChild(input);
        input.select();
        document.execCommand('copy');
        document.body.removeChild(input);
        import('antd').then(({ message }) => message.success('完整 URL 已复制'));
      }
    };

    return (
      <>
        <style>{`
        .api-path-input-custom.ant-input-group-wrapper,
        .api-path-input-custom .ant-input-wrapper,
        .api-path-input-custom .ant-input-group {
          height: 32px;
          width: 100%;
          display: flex;
          align-items: stretch;
        }
        /* 前缀随文案自适应，禁止被 flex 挤扁裁切 */
        .api-path-input-custom .ant-input-group-addon {
          display: inline-flex !important;
          align-items: center !important;
          justify-content: center;
          flex: 0 0 auto !important;
          width: auto !important;
          max-width: none !important;
          height: 32px !important;
          box-sizing: border-box;
          padding: 0 11px !important;
          white-space: nowrap !important;
          overflow: visible !important;
          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
        }
        .api-path-input-custom .ant-input-affix-wrapper {
          display: inline-flex !important;
          align-items: center !important;
          flex: 1 1 auto !important;
          min-width: 0 !important;
          height: 32px !important;
          box-sizing: border-box;
          padding-block: 0 !important;
        }
        .api-path-input-custom .ant-input-affix-wrapper > input.ant-input {
          height: 30px !important;
          line-height: 30px !important;
        }

        /* 全局正常状态（未发布）：前缀背景色置为白色，保持和主输入框一致 */
        .api-path-input-custom:not(.flow-api-path-disabled) .ant-input-group-addon {
          background-color: #ffffff !important;
        }

        /* 置灰状态（已发布）：覆盖外层 wrapper、input、前缀的背景色为灰色 */
        .flow-api-path-disabled,
        .flow-api-path-disabled .ant-input-affix-wrapper,
        .flow-api-path-disabled input,
        .flow-api-path-disabled .ant-input-group-addon {
          background-color: #f5f5f5 !important;
          color: rgba(0, 0, 0, 0.25) !important;
          cursor: pointer !important;
        }
        
        .flow-api-path-disabled:focus,
        .flow-api-path-disabled:focus-within,
        .flow-api-path-disabled .ant-input-affix-wrapper:focus,
        .flow-api-path-disabled .ant-input-affix-wrapper:focus-within {
           box-shadow: none !important;
           border-color: #d9d9d9 !important;
        }
      `}</style>
        <Input
          {...rest}
          ref={ref}
          size={size}
          className={`${className || ''} api-path-input-custom ${disabled ? 'flow-api-path-disabled' : ''}`.trim()}
          value={value}
          onChange={(e) => {
            if (!disabled) {
              onChange?.(e.target.value);
            }
          }}
          readOnly={disabled}
          onClick={(e) => {
            if (disabled) {
              import('antd').then(({ message }) =>
                message.info('当前接口已在线上运行，修改路径将导致现有调用方报错。若需修改，请先下线该接口。'),
              );
            }
            if (rest.onClick) {
              rest.onClick(e);
            }
          }}
          placeholder="相对路径，例如: /user/info"
          addonBefore={addonBefore}
          suffix={
            <Tooltip title="复制完整 URL">
              <CopyOutlined
                onClick={(e) => {
                  e.stopPropagation();
                  handleCopy();
                }}
                style={{ cursor: 'pointer', color: '#1677ff', transition: 'color 0.3s' }}
                onMouseEnter={(e) => (e.currentTarget.style.color = '#4096ff')}
                onMouseLeave={(e) => (e.currentTarget.style.color = '#1677ff')}
              />
            </Tooltip>
          }
          status={status}
          style={{ flex: 1, fontFamily: 'monospace', ...style }}
        />
      </>
    );
  },
);

ApiPathInput.displayName = 'ApiPathInput';

export default ApiPathInput;

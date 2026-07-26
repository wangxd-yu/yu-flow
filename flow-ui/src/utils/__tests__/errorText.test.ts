import { isUiHandled, isDemoRestricted, getErrorText, showActionError, markUiHandled } from '../errorText';

const messageError = jest.fn();
jest.mock('antd', () => ({
  message: {
    error: (...args: any[]) => messageError(...args),
  },
}));

describe('errorText utils', () => {
  beforeEach(() => {
    messageError.mockClear();
  });

  describe('markUiHandled / isUiHandled', () => {
    it('should mark and detect UI handled error', () => {
      const error = new Error('test');
      expect(isUiHandled(error)).toBe(false);
      markUiHandled(error);
      expect(isUiHandled(error)).toBe(true);
    });

    it('should return false for null/undefined', () => {
      expect(isUiHandled(null)).toBe(false);
      expect(isUiHandled(undefined)).toBe(false);
      expect(isUiHandled('string')).toBe(false);
    });
  });

  describe('isDemoRestricted', () => {
    it('should detect DEMO_RESTRICTED in string error', () => {
      expect(isDemoRestricted('DEMO_RESTRICTED')).toBe(true);
      expect(isDemoRestricted('something DEMO_RESTRICTED else')).toBe(true);
    });

    it('should detect DEMO_RESTRICTED in object error', () => {
      expect(isDemoRestricted({ response: { data: { msg: 'DEMO_RESTRICTED' } } })).toBe(true);
    });

    it('should not detect for other errors', () => {
      expect(isDemoRestricted('OTHER_ERROR')).toBe(false);
      expect(isDemoRestricted(new Error('normal error'))).toBe(false);
    });
  });

  describe('getErrorText', () => {
    it('should return fallback for empty error', () => {
      expect(getErrorText(null)).toBe('操作失败，请重试');
      expect(getErrorText(undefined, 'custom fallback')).toBe('custom fallback');
    });

    it('should normalize network errors', () => {
      expect(getErrorText('timeout')).toBe('请求超时，请稍后重试');
      expect(getErrorText('The request timed out')).toBe('请求超时，请稍后重试');
      expect(getErrorText('failed to fetch')).toBe('网络连接异常，请检查网络后重试');
      expect(getErrorText('network error')).toBe('网络连接异常，请检查网络后重试');
    });

    it('should translate known codes', () => {
      expect(getErrorText('SCRIPT_LANGUAGE_DISABLED')).toBe('该脚本语言未在白名单内，请联系管理员开启');
      expect(getErrorText('IGNORE_SSL_DISABLED')).toBe('当前环境已禁用跳过证书校验（ignoreSsl）');
      expect(getErrorText('OPEN_RATE_LIMITED')).toBe('调用过于频繁，请稍后再试');
      expect(getErrorText('OPEN_AUTH_DENIED')).toBe('平台已停用或未授权该接口');
    });

    it('should extract raw message from axios-like error', () => {
      expect(getErrorText({ response: { data: { msg: '后端业务错误' } } })).toBe('后端业务错误');
      expect(getErrorText({ response: { data: { message: '后端业务错误2' } } })).toBe('后端业务错误2');
      expect(getErrorText({ message: '普通错误' })).toBe('普通错误');
    });
  });

  describe('showActionError', () => {
    it('should toast when error is not handled and not demo restricted', () => {
      showActionError({ message: '后端业务错误' });
      expect(messageError).toHaveBeenCalledWith('后端业务错误');
    });

    it('should not toast when UI handled', () => {
      const error = new Error('handled');
      markUiHandled(error);
      showActionError(error);
      expect(messageError).not.toHaveBeenCalled();
    });

    it('should not toast for demo restricted', () => {
      showActionError('DEMO_RESTRICTED');
      expect(messageError).not.toHaveBeenCalled();
    });
  });
});

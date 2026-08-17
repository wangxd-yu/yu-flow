import React, { useState, useCallback, useEffect } from 'react';
import { message } from 'antd';
import { useNavigate, useModel, request } from '@umijs/max';
import { fetchAuthMe } from '@/services/auth';
import { clearAuthHint, setAuthHint } from '@/utils/session';
import { encryptLoginPassword } from '@/utils/sm2Login';
import styles from './index.module.css';
import logo from '@/assets/logo1.svg';

/* ── Inline SVG Icons ── */
const UserIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </svg>
);

const LockIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);

const EyeIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

const EyeOffIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
    <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
    <line x1="1" y1="1" x2="23" y2="23" />
  </svg>
);

const ShieldIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </svg>
);

declare global {
  interface Window {
    __DEMO_MODE__?: boolean;
  }
}

type CaptchaState = {
  captchaId: string;
  imageBase64: string;
};

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { setInitialState } = useModel('@@initialState');
  const [loading, setLoading] = useState(false);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    captchaCode: '',
  });
  const [captcha, setCaptcha] = useState<CaptchaState | null>(null);
  const [errors, setErrors] = useState<{
    username?: string;
    password?: string;
    captchaCode?: string;
  }>({});

  const refreshCaptcha = useCallback(async () => {
    setCaptchaLoading(true);
    try {
      const res = await request<{ captchaId: string; imageBase64: string }>(
        '/flow-api/login/captcha',
        { method: 'GET', skipErrorHandler: true } as any,
      );
      const data = (res as any)?.data || res;
      if (data?.captchaId && data?.imageBase64) {
        setCaptcha({
          captchaId: data.captchaId,
          imageBase64: data.imageBase64,
        });
        setFormData((prev) => ({ ...prev, captchaCode: '' }));
        setErrors((prev) => ({ ...prev, captchaCode: undefined }));
      } else {
        message.error('验证码加载失败');
      }
    } catch (e) {
      console.error(e);
      message.error('验证码加载失败，请检查后端与 Redis');
    } finally {
      setCaptchaLoading(false);
    }
  }, []);

  useEffect(() => {
    clearAuthHint();
    setInitialState((prev: any) => ({
      ...(prev || {}),
      name: '',
      displayName: '',
      isLogin: false,
      roles: [],
      permissions: [],
    }));

    if (typeof window !== 'undefined' && window.__DEMO_MODE__) {
      setFormData((prev) => ({
        ...prev,
        username: 'admin',
        password: '123456',
      }));
    }

    refreshCaptcha();
  }, [setInitialState, refreshCaptcha]);

  const validate = useCallback(() => {
    const e: { username?: string; password?: string; captchaCode?: string } = {};
    if (!formData.username.trim()) e.username = '请输入用户名';
    if (!formData.password) e.password = '请输入密码';
    if (!formData.captchaCode.trim()) e.captchaCode = '请输入验证码';
    if (!captcha?.captchaId) e.captchaCode = e.captchaCode || '请先获取验证码';
    setErrors(e);
    return Object.keys(e).length === 0;
  }, [formData, captcha]);

  const handleChange =
    (field: 'username' | 'password' | 'captchaCode') =>
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setFormData((prev) => ({ ...prev, [field]: e.target.value }));
      if (errors[field]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }));
      }
    };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    setLoading(true);
    try {
      const passwordCipher = await encryptLoginPassword(formData.password);
      await request('/flow-api/login', {
        method: 'POST',
        data: {
          username: formData.username,
          passwordCipher,
          captchaId: captcha?.captchaId,
          captchaCode: formData.captchaCode.trim(),
        },
      });

      setAuthHint();
      const me = await fetchAuthMe();
      if (!me?.permissions?.length) {
        clearAuthHint();
        message.error('获取用户权限失败，请确认后端已启动且 Cookie 可写入');
        refreshCaptcha();
        return;
      }
      await setInitialState((prev: any) => ({
        ...(prev || {}),
        name: me.username || formData.username,
        displayName: me.displayName || me.username || formData.username,
        isLogin: true,
        userId: me.userId,
        roles: me.roles || [],
        permissions: me.permissions,
        legacyAdmin: me.legacyAdmin,
        ossEnabled: me.ossEnabled !== false,
      }));
      message.success('登录成功');
      navigate('/home');
    } catch (error: any) {
      console.error(error);
      const msg = error?.message || error?.data?.msg;
      if (typeof msg === 'string' && msg.includes('公钥')) {
        message.error(msg);
      }
      // 失败后刷新验证码（已消费或错误）
      refreshCaptcha();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.leftSide}>
        <div className={styles.brandInfo}>
          <img src={logo} alt="YU Flow Logo" className={styles.hugeLogo} />
          <h1 className={styles.slogan}>
            企业级<br />
            低代码流程引擎
          </h1>
          <p className={styles.description}>
            YU Flow 提供强大的可视化编排能力，帮助您快速构建复杂的业务流程。
            安全、稳定、高效。
          </p>
        </div>
      </div>

      <div className={styles.rightSide}>
        <div className={styles.formContainer}>
          <div className={styles.formHeader}>
            <img src={logo} alt="YU Flow Logo" className={styles.mobileLogo} />
            <h2>欢迎回来</h2>
            <p>登录 YU Flow 控制台</p>
          </div>

          <form onSubmit={handleSubmit} noValidate>
            <div className={styles.inputGroup}>
              <label htmlFor="username">用户名</label>
              <div className={styles.inputWrapper}>
                <span className={styles.inputIcon}><UserIcon /></span>
                <input
                  id="username"
                  className={styles.inputField}
                  type="text"
                  placeholder="请输入用户名"
                  autoComplete="username"
                  value={formData.username}
                  onChange={handleChange('username')}
                />
              </div>
              {errors.username && <span className={styles.errorMsg}>{errors.username}</span>}
            </div>

            <div className={styles.inputGroup}>
              <label htmlFor="password">密码</label>
              <div className={styles.inputWrapper}>
                <span className={styles.inputIcon}><LockIcon /></span>
                <input
                  id="password"
                  className={styles.inputField}
                  type={showPassword ? 'text' : 'password'}
                  placeholder="请输入密码"
                  autoComplete="current-password"
                  value={formData.password}
                  onChange={handleChange('password')}
                />
                <button
                  type="button"
                  className={styles.passwordToggle}
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                >
                  {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
              {errors.password && <span className={styles.errorMsg}>{errors.password}</span>}
            </div>

            <div className={styles.inputGroup}>
              <label htmlFor="captchaCode">验证码</label>
              <div className={styles.captchaRow}>
                <div className={styles.inputWrapper}>
                  <span className={styles.inputIcon}><ShieldIcon /></span>
                  <input
                    id="captchaCode"
                    className={styles.inputField}
                    type="text"
                    placeholder="请输入验证码"
                    autoComplete="off"
                    maxLength={8}
                    value={formData.captchaCode}
                    onChange={handleChange('captchaCode')}
                  />
                </div>
                <button
                  type="button"
                  className={styles.captchaImgBtn}
                  onClick={refreshCaptcha}
                  disabled={captchaLoading}
                  title="点击刷新验证码"
                  aria-label="刷新验证码"
                >
                  {captcha?.imageBase64 ? (
                    <img
                      src={captcha.imageBase64}
                      alt="验证码"
                      className={styles.captchaImg}
                    />
                  ) : (
                    <span className={styles.captchaPlaceholder}>
                      {captchaLoading ? '加载中' : '点击获取'}
                    </span>
                  )}
                </button>
              </div>
              {errors.captchaCode && (
                <span className={styles.errorMsg}>{errors.captchaCode}</span>
              )}
            </div>

            <button type="submit" className={styles.submitBtn} disabled={loading}>
              {loading ? '登录中...' : '登录系统'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;

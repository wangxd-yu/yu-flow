import React, { useState, useCallback } from 'react';
import { message } from 'antd';
import { useNavigate } from '@umijs/max';
import { request } from '@umijs/max';
import styles from './index.module.css';

/* ── Inline SVG Icons (极简线条风格) ── */
const UserIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </svg>
);

const LockIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);

const EyeIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

const EyeOffIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
    <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
    <line x1="1" y1="1" x2="23" y2="23" />
  </svg>
);

const BrandIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="16 18 22 12 16 6" />
    <polyline points="8 6 2 12 8 18" />
    <line x1="14" y1="4" x2="10" y2="20" />
  </svg>
);

/* ── Login Page Component ── */
const Login: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({ username: '', password: '' });
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [focused, setFocused] = useState<string | null>(null);

  const validate = useCallback(() => {
    const e: { username?: string; password?: string } = {};
    if (!formData.username.trim()) e.username = '请输入用户名';
    if (!formData.password) e.password = '请输入密码';
    setErrors(e);
    return Object.keys(e).length === 0;
  }, [formData]);

  const handleChange = (field: 'username' | 'password') => (
    e: React.ChangeEvent<HTMLInputElement>,
  ) => {
    setFormData((prev) => ({ ...prev, [field]: e.target.value }));
    // Clear error on typing
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    setLoading(true);
    try {
      const response = await request('/flow-api/login', {
        method: 'POST',
        data: formData,
      });

      localStorage.setItem('flow_token', response);
      message.success('登录成功');
      navigate('/');
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSubmit(e as unknown as React.FormEvent);
    }
  };

  return (
    <div className={styles.loginPage}>
      {/* Extra glow element */}
      <div className={styles.glowBottomLeft} />

      <div className={styles.cardWrapper}>
        <form className={styles.card} onSubmit={handleSubmit} noValidate>
          {/* Brand mark */}
          <div className={styles.brandMark}>
            <BrandIcon />
          </div>

          {/* Header */}
          <div className={styles.headerSection}>
            <h1 className={styles.title}>系统登录</h1>
            <p className={styles.subtitle}>欢迎使用 YU Flow 平台</p>
          </div>

          {/* Username field */}
          <div className={styles.formGroup}>
            <label className={styles.label} htmlFor="login-username">
              用户名
            </label>
            <div className={styles.inputWrapper}>
              <span className={styles.inputIcon}>
                <UserIcon />
              </span>
              <input
                id="login-username"
                className={styles.input}
                type="text"
                placeholder="请输入用户名"
                autoComplete="username"
                value={formData.username}
                onChange={handleChange('username')}
                onFocus={() => setFocused('username')}
                onBlur={() => setFocused(null)}
                onKeyDown={handleKeyDown}
              />
            </div>
            {errors.username && (
              <div className={styles.errorText}>{errors.username}</div>
            )}
          </div>

          {/* Password field */}
          <div className={styles.formGroup}>
            <label className={styles.label} htmlFor="login-password">
              密码
            </label>
            <div className={styles.inputWrapper}>
              <span className={styles.inputIcon}>
                <LockIcon />
              </span>
              <input
                id="login-password"
                className={styles.input}
                type={showPassword ? 'text' : 'password'}
                placeholder="请输入密码"
                autoComplete="current-password"
                value={formData.password}
                onChange={handleChange('password')}
                onFocus={() => setFocused('password')}
                onBlur={() => setFocused(null)}
                onKeyDown={handleKeyDown}
              />
              <button
                type="button"
                className={styles.passwordToggle}
                onClick={() => setShowPassword((v) => !v)}
                tabIndex={-1}
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
              >
                {showPassword ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
            {errors.password && (
              <div className={styles.errorText}>{errors.password}</div>
            )}
          </div>

          {/* Submit button */}
          <button
            type="submit"
            className={styles.submitButton}
            disabled={loading}
          >
            <span className={styles.buttonContent}>
              {loading && <span className={styles.spinner} />}
              {loading ? '登录中...' : '登 录'}
            </span>
          </button>

          {/* Footer */}
          <div className={styles.footer}>
            <p className={styles.footerText}>YU Flow · 低代码流程引擎</p>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Login;

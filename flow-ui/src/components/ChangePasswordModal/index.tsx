import React, { useMemo, useState } from 'react';
import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { history, request, useModel } from '@umijs/max';
import { clearAuthHint } from '@/utils/session';
import { evaluatePassword, passwordComplexityValidator } from '@/utils/passwordPolicy';
import styles from './index.module.css';

export type ChangePasswordModalProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

const ChangePasswordModal: React.FC<ChangePasswordModalProps> = ({
  open,
  onOpenChange,
}) => {
  const { initialState } = useModel('@@initialState');
  const [newPwd, setNewPwd] = useState('');
  const evalResult = useMemo(() => evaluatePassword(newPwd), [newPwd]);

  const strengthClass = (n: number) => {
    if (evalResult.score >= n) {
      const map: Record<number, string> = {
        1: styles.strengthSegOn1,
        2: styles.strengthSegOn2,
        3: styles.strengthSegOn3,
        4: styles.strengthSegOn4,
        5: styles.strengthSegOn5,
      };
      return `${styles.strengthSeg} ${map[Math.min(evalResult.score, 5)] || styles.strengthSegOn4}`;
    }
    return styles.strengthSeg;
  };

  return (
    <ModalForm
      title="修改密码"
      open={open}
      width={440}
      modalProps={{
        destroyOnClose: true,
        centered: true,
        maskClosable: false,
        onCancel: () => onOpenChange(false),
      }}
      submitter={{
        searchConfig: { submitText: '确认修改', resetText: '取消' },
      }}
      onOpenChange={(v) => {
        if (!v) setNewPwd('');
        onOpenChange(v);
      }}
      onFinish={async (values) => {
        try {
          await request('/flow-api/auth/change-password', {
            method: 'POST',
            data: {
              oldPassword: values.oldPassword,
              newPassword: values.newPassword,
              confirmPassword: values.confirmPassword,
            },
          });
          message.success('密码已更新，请重新登录');
          onOpenChange(false);
          clearAuthHint();
          history.push('/login');
          return true;
        } catch {
          return false;
        }
      }}
    >
      <div className={styles.hint}>
        为保障账号安全，新密码需同时满足长度与字符类型要求。
        {initialState?.legacyAdmin
          ? ' 当前为配置文件引导账号，改密成功后将写入数据库，请使用新密码重新登录。'
          : ' 修改成功后将退出登录，请使用新密码重新登录。'}
      </div>

      <ProFormText.Password
        name="oldPassword"
        label="当前密码"
        placeholder="请输入当前密码"
        rules={[{ required: true, message: '请输入当前密码' }]}
        fieldProps={{ autoComplete: 'current-password' }}
      />

      <ProFormText.Password
        name="newPassword"
        label="新密码"
        placeholder="请设置新密码"
        rules={[
          { required: true, message: '请输入新密码' },
          { validator: passwordComplexityValidator },
        ]}
        fieldProps={{
          autoComplete: 'new-password',
          onChange: (e) => setNewPwd(e.target.value || ''),
        }}
      />

      <div className={styles.strengthWrap}>
        <div className={styles.strengthBar}>
          {[1, 2, 3, 4].map((n) => (
            <div key={n} className={strengthClass(n)} />
          ))}
        </div>
        <div className={styles.strengthLabel}>
          密码强度：<strong>{newPwd ? evalResult.levelLabel : '—'}</strong>
        </div>
      </div>

      <div className={styles.rules}>
        {evalResult.checks.map((c) => (
          <div
            key={c.key}
            className={`${styles.rule} ${c.ok ? styles.ruleOk : ''}`}
          >
            <span className={styles.ruleDot}>{c.ok ? '✓' : ''}</span>
            {c.label}
          </div>
        ))}
      </div>

      <ProFormText.Password
        name="confirmPassword"
        label="确认新密码"
        placeholder="请再次输入新密码"
        dependencies={['newPassword']}
        rules={[
          { required: true, message: '请确认新密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (!value || getFieldValue('newPassword') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('两次输入的新密码不一致'));
            },
          }),
        ]}
        fieldProps={{ autoComplete: 'new-password' }}
      />
    </ModalForm>
  );
};

export default ChangePasswordModal;

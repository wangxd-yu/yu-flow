import PrivacyMaskRuleList, { normalizePrivacyMaskRule } from '@/components/flow/PrivacyMaskRuleList';
import {
  getHostPrivacyProfiles,
  hydratePrivacyCrypto,
  privacyFamilyOf,
  privacyRuleSummary,
  privacySpecLabel,
  saveHostPrivacyProfiles,
  type HostPrivacyProfile,
  type PrivacyMaskRule,
} from '@/services/flow/hostConfig';
import { PlusOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useCallback, useEffect, useMemo, useState } from 'react';

type Props = {
  canWrite: boolean;
};

type ProfileForm = HostPrivacyProfile & { decryptKey?: string };

const ALG_OPTIONS = [
  { value: 'SM4', label: 'SM4' },
  { value: 'AES', label: 'AES' },
  { value: 'PLAIN', label: '已是明文（只脱敏）' },
];

const MODE_OPTIONS = [
  { value: 'CBC', label: 'CBC' },
  { value: 'ECB', label: 'ECB' },
  { value: 'GCM', label: 'GCM' },
];

const ENC_OPTIONS = [
  { value: 'HEX', label: 'HEX' },
  { value: 'BASE64', label: 'Base64' },
];

const IV_OPTIONS = [
  { value: 'PREPEND', label: '前置' },
  { value: 'NONE', label: '无' },
  { value: 'FIXED', label: '固定' },
];

const DEFAULT_RULES: PrivacyMaskRule[] = [
  {
    matchMode: 'EXACT',
    aliases: ['phone', 'mobile', 'tel', '手机'],
    method: 'KEEP_HEAD_TAIL',
    keepHead: 3,
    keepTail: 4,
  },
  {
    matchMode: 'EXACT',
    aliases: ['name', 'realName', 'userName', '姓名'],
    method: 'NAME_KEEP_ENDS',
  },
  {
    matchMode: 'EXACT',
    aliases: ['idCard', 'idNo', 'certNo', 'id_no', '身份证'],
    method: 'KEEP_HEAD_TAIL',
    keepHead: 1,
    keepTail: 1,
  },
];

function newId(): string {
  return `p_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

function toUiMatchMode(mode?: string): 'EXACT' | 'CONTAINS' {
  return mode === 'CONTAINS' ? 'CONTAINS' : 'EXACT';
}

function emptyForm(template?: boolean): ProfileForm {
  return {
    id: newId(),
    name: template ? '默认 SM4' : '',
    decryptAlg: 'SM4',
    decryptMode: 'CBC',
    decryptEncoding: 'HEX',
    decryptIvMode: 'PREPEND',
    decryptIvFixed: '',
    decryptKey: '',
    fieldSuffix: '_encrypt',
    extraFields: [],
    stripSuffix: true,
    rules: template ? DEFAULT_RULES.map((r) => ({ ...r, aliases: [...(r.aliases || [])] })) : [],
  };
}

const LABEL_COL = { style: { width: 88 } };
const WRAPPER_COL = { style: { flex: 1, minWidth: 0 } };

const PrivacyProfilesCard: React.FC<Props> = ({ canWrite }) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [profiles, setProfiles] = useState<HostPrivacyProfile[]>([]);
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form] = Form.useForm<ProfileForm>();
  const decryptAlg = Form.useWatch('decryptAlg', form);
  const decryptMode = Form.useWatch('decryptMode', form);
  const decryptIvMode = Form.useWatch('decryptIvMode', form);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getHostPrivacyProfiles();
      setProfiles(Array.isArray(data.profiles) ? data.profiles : []);
    } catch {
      message.error('加载隐私方案失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const persist = async (next: HostPrivacyProfile[]) => {
    setSaving(true);
    try {
      const saved = await saveHostPrivacyProfiles(next);
      setProfiles(Array.isArray(saved.profiles) ? saved.profiles : next);
      message.success('已保存隐私方案');
    } catch (e: any) {
      message.error(e?.message || '保存失败');
      throw e;
    } finally {
      setSaving(false);
    }
  };

  const openCreate = (template = false) => {
    setEditingId(null);
    form.setFieldsValue(emptyForm(template));
    setOpen(true);
  };

  const openEdit = (row: HostPrivacyProfile) => {
    setEditingId(row.id || null);
    form.setFieldsValue({
      ...row,
      ...hydratePrivacyCrypto(row),
      decryptKey: '',
      extraFields: row.extraFields || [],
      rules: (row.rules || []).map((r) => ({
        ...r,
        aliases: r.aliases || [],
        matchMode: toUiMatchMode(r.matchMode),
      })),
      stripSuffix: row.stripSuffix !== false,
    });
    setOpen(true);
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const crypto = hydratePrivacyCrypto(values);
    const payload: HostPrivacyProfile = {
      id: values.id || newId(),
      name: (values.name || '').trim(),
      decryptAlg: crypto.decryptAlg,
      decryptMode: crypto.decryptAlg === 'PLAIN' ? undefined : crypto.decryptMode,
      decryptEncoding: crypto.decryptAlg === 'PLAIN' ? undefined : crypto.decryptEncoding,
      decryptIvMode:
        crypto.decryptAlg === 'PLAIN'
          ? undefined
          : crypto.decryptMode === 'ECB'
            ? 'NONE'
            : crypto.decryptIvMode,
      decryptIvFixed:
        crypto.decryptAlg !== 'PLAIN' && crypto.decryptIvMode === 'FIXED'
          ? values.decryptIvFixed?.trim() || undefined
          : undefined,
      fieldSuffix: values.fieldSuffix?.trim() || undefined,
      extraFields: values.extraFields || [],
      stripSuffix: values.stripSuffix !== false,
      rules: (values.rules || []).map((r) => normalizePrivacyMaskRule(r)),
    };
    const typedKey = typeof values.decryptKey === 'string' ? values.decryptKey.trim() : '';
    if (typedKey) payload.decryptKey = typedKey;

    const next = [...profiles];
    const idx = next.findIndex((p) => p.id === payload.id);
    if (idx >= 0) next[idx] = { ...next[idx], ...payload };
    else next.push(payload);
    await persist(next);
    setOpen(false);
  };

  const handleDelete = async (id?: string) => {
    if (!id) return;
    await persist(profiles.filter((p) => p.id !== id));
  };

  const columns: ColumnsType<HostPrivacyProfile> = useMemo(
    () => [
      {
        title: '方案',
        dataIndex: 'name',
        render: (_, row) => (
          <div>
            <div className="host-dim-title">{row.name || row.id}</div>
            <Typography.Text type="secondary" className="host-dim-hint">
              {row.id}
            </Typography.Text>
          </div>
        ),
      },
      {
        title: '解密',
        width: 160,
        render: (_, row) => (
          <Space size={6} wrap>
            <Tag>{privacySpecLabel(row)}</Tag>
            {row.decryptKeySet ? <Tag color="success">已配密钥</Tag> : <Tag>YAML 回退</Tag>}
          </Space>
        ),
      },
      {
        title: '脱敏',
        render: (_, row) => privacyRuleSummary(row),
      },
      {
        title: '操作',
        width: 140,
        render: (_, row) => (
          <Space>
            <Button type="link" size="small" disabled={!canWrite} onClick={() => openEdit(row)}>
              编辑
            </Button>
            <Popconfirm
              title="删除该方案？"
              description="已引用它的目录/接口将解密失败关闭为 ****"
              onConfirm={() => handleDelete(row.id)}
              disabled={!canWrite}
            >
              <Button type="link" size="small" danger disabled={!canWrite}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [canWrite, profiles],
  );

  const editing = profiles.find((p) => p.id === editingId);
  const family = privacyFamilyOf(decryptAlg);
  const keyExtra =
    family === 'AES'
      ? '16/24/32 字节或对应 hex，不复用数据源 AES'
      : family === 'PLAIN'
        ? '明文方案无需密钥'
        : '16 字节或 32 位 hex；空则用 YAML 密钥';
  const modeOptions =
    family === 'AES' ? MODE_OPTIONS : MODE_OPTIONS.filter((o) => o.value !== 'GCM');
  const ivOptions =
    decryptMode === 'GCM' ? IV_OPTIONS.filter((o) => o.value !== 'NONE') : IV_OPTIONS;

  return (
    <Card
      className="host-principal-card"
      loading={loading}
      title={
        <Space size={10}>
          <span>隐私解密与脱敏方案</span>
          <Tag>{profiles.length} 套</Tag>
        </Space>
      }
      extra={
        <Space>
          <Button size="small" disabled={!canWrite} onClick={() => openCreate(true)}>
            添加默认模板
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<PlusOutlined />}
            disabled={!canWrite}
            onClick={() => openCreate(false)}
          >
            添加方案
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        className="host-principal-notice"
        message="目录和接口下拉选择这里的方案。谁看明文仍由「当前用户解析」里的角色码决定。"
      />

      <Table<HostPrivacyProfile>
        rowKey={(r) => r.id || r.name || ''}
        size="middle"
        pagination={false}
        columns={columns}
        dataSource={profiles}
        locale={{ emptyText: '尚未配置方案，拦截时回退 YAML 的 SM4 密钥与内置脱敏' }}
      />

      <Drawer
        className="host-privacy-drawer"
        title={editingId ? '编辑隐私方案' : '新建隐私方案'}
        open={open}
        onClose={() => setOpen(false)}
        width={600}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} disabled={!canWrite} onClick={() => void handleOk()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form
          form={form}
          layout="horizontal"
          size="small"
          className="host-privacy-drawer-form"
          labelAlign="right"
          labelCol={LABEL_COL}
          wrapperCol={WRAPPER_COL}
          colon
          requiredMark
          onValuesChange={(changed, all) => {
            if (changed.decryptAlg && privacyFamilyOf(changed.decryptAlg) === 'SM4' && all.decryptMode === 'GCM') {
              form.setFieldValue('decryptMode', 'CBC');
            }
            if (changed.decryptMode === 'ECB') {
              form.setFieldValue('decryptIvMode', 'NONE');
            }
            if (changed.decryptMode === 'CBC' && all.decryptIvMode === 'NONE') {
              form.setFieldValue('decryptIvMode', 'PREPEND');
            }
            if (changed.decryptMode === 'GCM' && all.decryptIvMode === 'NONE') {
              form.setFieldValue('decryptIvMode', 'PREPEND');
            }
          }}
        >
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '填写名称' }]}>
            <Input placeholder="如 员工档案 SM4" maxLength={64} />
          </Form.Item>
          <Form.Item name="decryptAlg" label="解密算法" rules={[{ required: true }]}>
            <Select options={ALG_OPTIONS} />
          </Form.Item>
          {family !== 'PLAIN' ? (
            <>
              <Form.Item
                label={
                  <Tooltip title="GCM 仅 AES。前置=密文开头为 IV；无=ECB 或 CBC 全 0 IV；固定=下方填写。">
                    <span>密文格式</span>
                  </Tooltip>
                }
              >
                <div className="host-privacy-crypto-row">
                  <Form.Item name="decryptMode" noStyle>
                    <Select options={modeOptions} popupMatchSelectWidth={false} />
                  </Form.Item>
                  <Form.Item name="decryptEncoding" noStyle>
                    <Select options={ENC_OPTIONS} popupMatchSelectWidth={false} />
                  </Form.Item>
                  {decryptMode !== 'ECB' ? (
                    <Form.Item name="decryptIvMode" noStyle>
                      <Select options={ivOptions} popupMatchSelectWidth={false} />
                    </Form.Item>
                  ) : (
                    <Form.Item name="decryptIvMode" hidden>
                      <Input />
                    </Form.Item>
                  )}
                </div>
              </Form.Item>
              {decryptMode !== 'ECB' && decryptIvMode === 'FIXED' ? (
                <Form.Item
                  name="decryptIvFixed"
                  label="固定 IV"
                  rules={[{ required: true, message: '填写固定 IV' }]}
                >
                  <Input placeholder={decryptMode === 'GCM' ? '12/16 字节或 hex' : '16 字节或 32 位 hex'} />
                </Form.Item>
              ) : null}
              <Form.Item
                name="decryptKey"
                label={
                  <Tooltip title={editing?.decryptKeySet ? `已配置，留空不改。${keyExtra}` : keyExtra}>
                    <span>库内密钥</span>
                  </Tooltip>
                }
              >
                <Input.Password placeholder={editing?.decryptKeySet ? '已配置，留空不改' : '密钥不回显'} />
              </Form.Item>
            </>
          ) : null}
          <Form.Item name="fieldSuffix" label="识别后缀">
            <Input placeholder="_encrypt" />
          </Form.Item>
          <Form.Item name="stripSuffix" label="去掉后缀" valuePropName="checked">
            <Switch checkedChildren="去掉" unCheckedChildren="保留" />
          </Form.Item>
          <Form.Item name="extraFields" label="补充字段">
            <Select mode="tags" tokenSeparators={[',']} placeholder="无后缀字段，如 id_no" />
          </Form.Item>
          <Form.Item
            label={
              <Tooltip title="精确=字段名全等；包含=字段名含别名。去后缀后匹配，未命中输出 ****。">
                <span>脱敏规则</span>
              </Tooltip>
            }
          >
            <PrivacyMaskRuleList name="rules" />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
};

export default PrivacyProfilesCard;

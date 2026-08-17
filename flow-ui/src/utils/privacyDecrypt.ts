import { sm2, sm4 } from 'sm-crypto';
import { request } from '@umijs/max';
import { SM2_CIPHER_MODE_C1C3C2 } from '@/utils/sm2Login';

/** 与后端 PrivacyCryptoService.HEADER_PRIVACY_KEY 对齐 */
export const PRIVACY_KEY_HEADER = 'X-Privacy-Key';

type PublicKeyResponse = {
  algorithm?: string;
  publicKey?: string;
  cipherMode?: number;
};

function unwrapData<T>(res: unknown): T {
  const r = res as { data?: T } | T;
  if (r && typeof r === 'object' && 'data' in (r as object) && (r as { data?: T }).data != null) {
    return (r as { data: T }).data;
  }
  return r as T;
}

function randomSm4KeyHex(): string {
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i += 1) bytes[i] = Math.floor(Math.random() * 256);
  }
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export type PrivacySession = {
  /** 放入请求头 X-Privacy-Key 的 SM2 密文（hex） */
  headerValue: string;
  /** 16 字节会话密钥的 hex，用于解开响应里的 __p 信封 */
  sm4KeyHex: string;
};

/**
 * 生成一次性 SM4 会话密钥，并用登录 SM2 公钥加密后作为请求头。
 * 宿主 / Amis 在调用已发布 JSON 接口（明文档）前调用一次即可。
 */
export async function createPrivacySession(): Promise<PrivacySession> {
  const res = await request<PublicKeyResponse>('/flow-api/login/public-key', {
    method: 'GET',
    skipErrorHandler: true,
  } as any);
  const data = unwrapData<PublicKeyResponse>(res);
  const publicKey = data?.publicKey?.trim();
  if (!publicKey) {
    throw new Error('获取 SM2 公钥失败，无法建立隐私传输会话');
  }
  const mode =
    typeof data.cipherMode === 'number' ? data.cipherMode : SM2_CIPHER_MODE_C1C3C2;
  const sm4KeyHex = randomSm4KeyHex();
  const headerValue = sm2.doEncrypt(sm4KeyHex, publicKey, mode);
  return { headerValue, sm4KeyHex };
}

export function isPrivacyEnvelope(value: unknown): value is { __p: number; alg?: string; v: string } {
  if (!value || typeof value !== 'object') return false;
  const node = value as Record<string, unknown>;
  return node.__p === 1 && typeof node.v === 'string';
}

function decryptEnvelope(envelope: { v: string }, sm4KeyHex: string): string {
  const hex = envelope.v.startsWith('0x') ? envelope.v.slice(2) : envelope.v;
  if (hex.length < 34) return '';
  const iv = hex.slice(0, 32);
  const body = hex.slice(32);
  return sm4.decrypt(body, sm4KeyHex, { mode: 'cbc', iv, padding: 'pkcs#5' }) as string;
}

/** 递归解开响应 JSON 中的 { __p:1, alg:'SM4', v } 字段，脱敏串原样保留。 */
export function unwrapPrivacyTree(node: unknown, sm4KeyHex: string): unknown {
  if (node == null) return node;
  if (isPrivacyEnvelope(node)) {
    try {
      return decryptEnvelope(node, sm4KeyHex);
    } catch {
      return '****';
    }
  }
  if (Array.isArray(node)) {
    return node.map((item) => unwrapPrivacyTree(item, sm4KeyHex));
  }
  if (typeof node === 'object') {
    const out: Record<string, unknown> = {};
    Object.entries(node as Record<string, unknown>).forEach(([k, v]) => {
      out[k] = unwrapPrivacyTree(v, sm4KeyHex);
    });
    return out;
  }
  return node;
}

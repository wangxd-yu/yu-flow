import { sm2 } from 'sm-crypto';
import { request } from '@umijs/max';

/** 与后端 LoginSm2CryptoService / Hutool SM2Engine.Mode.C1C3C2 对齐 */
export const SM2_CIPHER_MODE_C1C3C2 = 1;

type PublicKeyResponse = {
  algorithm?: string;
  publicKey?: string;
  cipherMode?: number;
  ttlSeconds?: number;
};

function unwrapData<T>(res: unknown): T {
  const r = res as { data?: T } | T;
  if (r && typeof r === 'object' && 'data' in (r as object) && (r as { data?: T }).data != null) {
    return (r as { data: T }).data;
  }
  return r as T;
}

/**
 * 拉取登录 SM2 公钥并用其加密口令。
 * 载荷：`{epochMillis}:{password}`；密文为 hex（无 04 前缀，与 sm-crypto 一致）。
 */
export async function encryptLoginPassword(password: string): Promise<string> {
  const res = await request<PublicKeyResponse>('/flow-api/login/public-key', {
    method: 'GET',
    skipErrorHandler: true,
  } as any);
  const data = unwrapData<PublicKeyResponse>(res);
  const publicKey = data?.publicKey?.trim();
  if (!publicKey) {
    throw new Error('获取登录公钥失败');
  }
  const mode =
    typeof data.cipherMode === 'number' ? data.cipherMode : SM2_CIPHER_MODE_C1C3C2;
  const payload = `${Date.now()}:${password}`;
  // sm-crypto：公钥可为 04 开头的未压缩 hex
  return sm2.doEncrypt(payload, publicKey, mode);
}

import crypto from 'crypto';

export function gcm(key: any, iv: any) {
  return crypto.createCipheriv('aes-256-gcm', key, iv);
}

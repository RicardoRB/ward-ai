import crypto from 'crypto';

export function cbc(key: any, iv: any) {
  return crypto.createCipheriv('aes-256-cbc', key, iv);
}

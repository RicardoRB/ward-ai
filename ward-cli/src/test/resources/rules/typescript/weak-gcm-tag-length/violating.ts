import crypto from 'node:crypto';

export function shortTag(key: any, iv: any) {
  return crypto.createCipheriv('aes-256-gcm', key, iv, { authTagLength: 4 });
}

export function anyLength(key: any, iv: any) {
  return crypto.createDecipheriv('aes-128-gcm', key, iv);
}

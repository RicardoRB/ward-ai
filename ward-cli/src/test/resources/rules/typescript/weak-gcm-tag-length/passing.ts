import crypto from 'node:crypto';

export function pinned(key: any, iv: any) {
  return crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
}

export function encrypt(key: any, iv: any) {
  return crypto.createCipheriv('aes-256-gcm', key, iv);
}

export function tagFifteen(key: any, iv: any) {
  return crypto.createCipheriv('aes-256-gcm', key, iv, { authTagLength: 15 });
}

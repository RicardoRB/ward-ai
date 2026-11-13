import crypto from 'node:crypto';

export function pinned(key, iv) {
  return crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
}

export function encrypt(key, iv) {
  return crypto.createCipheriv('aes-256-gcm', key, iv);
}

export function tagFifteen(key, iv) {
  return crypto.createCipheriv('aes-256-gcm', key, iv, { authTagLength: 15 });
}

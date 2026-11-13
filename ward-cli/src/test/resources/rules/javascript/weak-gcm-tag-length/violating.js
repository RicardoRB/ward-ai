import crypto from 'node:crypto';

export function shortTag(key, iv) {
  return crypto.createCipheriv('aes-256-gcm', key, iv, { authTagLength: 4 });
}

export function anyLength(key, iv) {
  return crypto.createDecipheriv('aes-128-gcm', key, iv);
}

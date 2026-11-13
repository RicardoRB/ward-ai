import crypto from 'node:crypto';

export function open(key: any, iv: any, tag: any, data: any) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(data), decipher.final()]);
}

export function stream(key: any, iv: any) {
  return crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
}

export function cbc(key: any, iv: any, data: any) {
  const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
  return cipher.update(data);
}

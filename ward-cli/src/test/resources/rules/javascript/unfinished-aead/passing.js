import crypto from 'node:crypto';

export function open(key, iv, tag, data) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(data), decipher.final()]);
}

export function stream(key, iv) {
  return crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
}

export function cbc(key, iv, data) {
  const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
  return cipher.update(data);
}

import crypto from 'node:crypto';

export function open(key: any, iv: any, tag: any, data: any) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  decipher.setAuthTag(tag);
  return decipher.update(data);
}

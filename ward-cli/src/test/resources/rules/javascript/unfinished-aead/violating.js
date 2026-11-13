import crypto from 'node:crypto';

export function open(key, iv, tag, data) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  decipher.setAuthTag(tag);
  return decipher.update(data);
}

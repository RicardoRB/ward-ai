import crypto from 'crypto';

export function gcm(key, iv) {
  return crypto.createCipheriv('aes-256-gcm', key, iv);
}

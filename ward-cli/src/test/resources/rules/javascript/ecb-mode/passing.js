import crypto from 'crypto';

export function cbc(key, iv) {
  return crypto.createCipheriv('aes-256-cbc', key, iv);
}

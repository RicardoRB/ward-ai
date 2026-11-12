import crypto from 'crypto';

export function random(key) {
  const iv = crypto.randomBytes(16);
  return crypto.createCipheriv('aes-256-cbc', key, iv);
}

export function inline(key) {
  return crypto.createCipheriv('aes-256-cbc', key, crypto.randomBytes(16));
}

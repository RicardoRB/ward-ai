import crypto from 'crypto';

export function des(key, iv) {
  return crypto.createCipheriv('des-ede3-cbc', key, iv);
}

export function rc4(key) {
  return crypto.createCipheriv('rc4', key, null);
}

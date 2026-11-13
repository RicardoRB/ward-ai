import crypto from 'crypto';

export function des(key: any, iv: any) {
  return crypto.createCipheriv('des-ede3-cbc', key, iv);
}

export function rc4(key: any) {
  return crypto.createCipheriv('rc4', key, null);
}

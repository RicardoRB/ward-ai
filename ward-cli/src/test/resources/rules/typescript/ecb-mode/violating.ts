import crypto from 'crypto';

export function ecb(key: any) {
  return crypto.createCipheriv('aes-128-ecb', key, null);
}

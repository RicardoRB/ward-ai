import crypto from 'crypto';

export function zero(key: any) {
  return crypto.createCipheriv('aes-256-cbc', key, Buffer.alloc(16));
}

export function literal(key: any) {
  return crypto.createCipheriv('aes-256-cbc', key, 'abcdefghijklmnop');
}

import crypto from 'crypto';

export function sha256(data: any) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

export function hmac(data: any, key: any) {
  return crypto.createHmac('sha256', key).update(data).digest('hex');
}

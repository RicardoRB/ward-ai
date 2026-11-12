import crypto from 'crypto';

export function sha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

export function hmac(data, key) {
  return crypto.createHmac('sha256', key).update(data).digest('hex');
}

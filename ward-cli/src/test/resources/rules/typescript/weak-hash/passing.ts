import crypto from 'crypto';

export function sha256(data: any) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

export function hmac(data: any, key: any) {
  return crypto.createHmac('sha256', key).update(data).digest('hex');
}

// HMAC does not rely on collision resistance (RFC 6151, RFC 6194)
export function legacyHmac(data: any, key: any) {
  return crypto.createHmac('sha1', key).update(data).digest('hex');
}

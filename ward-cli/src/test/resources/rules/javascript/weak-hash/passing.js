import crypto from 'crypto';

export function sha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

export function hmac(data, key) {
  return crypto.createHmac('sha256', key).update(data).digest('hex');
}

// HMAC does not rely on collision resistance (RFC 6151, RFC 6194)
export function legacyHmac(data, key) {
  return crypto.createHmac('sha1', key).update(data).digest('hex');
}

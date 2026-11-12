import crypto from 'crypto';

export function strong() {
  return crypto.generateKeyPairSync('rsa', { modulusLength: 3072 });
}

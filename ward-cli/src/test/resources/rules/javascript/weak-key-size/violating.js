import crypto from 'crypto';

export function short() {
  return crypto.generateKeyPairSync('rsa', { modulusLength: 1024 });
}

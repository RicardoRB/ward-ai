import crypto from 'crypto';

export function token() {
  return crypto.randomUUID();
}

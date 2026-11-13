import crypto from 'crypto';

export function md5(data) {
  return crypto.createHash('md5').update(data).digest('hex');
}

export function sha1(data, key) {
  return crypto.createHmac('sha1', key).update(data).digest('hex');
}

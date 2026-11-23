import crypto from 'crypto';

export function md5(data: any) {
  return crypto.createHash('md5').update(data).digest('hex');
}

export function sha1(data: any, key: any) {
  return crypto.createHash('sha1').update(data).digest('hex');
}

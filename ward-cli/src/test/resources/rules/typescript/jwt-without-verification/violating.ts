import jwt from 'jsonwebtoken';

export function verify(token: any, key: any) {
  return jwt.verify(token, key, {
    algorithms: ['none'],
  });
}

export function sign(payload: any) {
  return jwt.sign(payload, '', {
    algorithm: 'none',
  });
}

import jwt from 'jsonwebtoken';

export function verify(token, key) {
  return jwt.verify(token, key, {
    algorithms: ['none'],
  });
}

export function sign(payload) {
  return jwt.sign(payload, '', {
    algorithm: 'none',
  });
}

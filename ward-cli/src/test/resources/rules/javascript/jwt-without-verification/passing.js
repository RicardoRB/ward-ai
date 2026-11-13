import jwt from 'jsonwebtoken';

export function verify(token, key) {
  return jwt.verify(token, key, {
    algorithms: ['RS256'],
  });
}

import jwt from 'jsonwebtoken';

export function verify(token: any, key: any) {
  return jwt.verify(token, key, {
    algorithms: ['RS256'],
  });
}

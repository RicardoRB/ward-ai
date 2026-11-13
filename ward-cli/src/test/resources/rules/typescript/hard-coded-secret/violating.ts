import jwt from 'jsonwebtoken';
import session from 'express-session';

export const sessions = session({
  secret: 'keyboard cat',
});

export function sign(payload: any) {
  return jwt.sign(payload, 'shhhhh', { expiresIn: '1h' });
}

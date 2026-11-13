import jwt from 'jsonwebtoken';
import session from 'express-session';

export const sessions = session({
  secret: process.env.SESSION_SECRET,
});

export function sign(payload: any) {
  return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn: '1h' });
}

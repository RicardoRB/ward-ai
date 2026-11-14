import crypto from 'crypto';

export function token() {
  return crypto.randomUUID();
}

// A random value that holds no secret is fine: UI choices, test data and operands.
export function pick(items) {
  const choice = items[Math.floor(Math.random() * items.length)];
  return choice;
}

export function captcha() {
  const left = Math.floor(Math.random() * 10);
  const right = Math.floor(Math.random() * 10);
  return { left, right };
}

export function jitter() {
  const delay = 100 + Math.random() * 50;
  return delay;
}

export function identity() {
  const identity = Math.random() > 0.5;
  return identity;
}

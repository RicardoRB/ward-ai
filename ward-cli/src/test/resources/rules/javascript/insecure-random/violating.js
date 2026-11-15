export function token() {
  return Math.random().toString(36);
}

export function declared() {
  const sessionToken = Math.random().toString(36);
  return sessionToken;
}

export function assigned(user) {
  user.resetKey = String(Math.random());
}

export const options = { apiSecret: Math.random().toString(16) };

export class Issuer {
  nonce = Math.random();

  newId() {
    return Math.floor(Math.random() * 1e9);
  }
}

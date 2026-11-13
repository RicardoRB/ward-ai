export function login(res) {
  res.cookie('id', '1', { sameSite: 'lax' });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { sameSite: 'strict' } });
}

export function forwarded(res, options) {
  res.cookie('id', '1', options);
}

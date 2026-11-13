export function login(res) {
  res.cookie('id', '1', { httpOnly: true });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { secure: true } });
}

export function forwarded(res, options) {
  res.cookie('id', '1', options);
}

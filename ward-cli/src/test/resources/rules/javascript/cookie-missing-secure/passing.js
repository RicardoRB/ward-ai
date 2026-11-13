export function login(res) {
  res.cookie('id', '1', { secure: true });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { secure: 'auto' } });
}

export function forwarded(res, options) {
  res.cookie('id', '1', options);
}

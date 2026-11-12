export function login(res) {
  res.cookie('id', '1', { httpOnly: true, secure: true });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { secure: true, httpOnly: true } });
}

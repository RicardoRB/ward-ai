export function login(res) {
  res.cookie('id', '1');
}

export function withOptions(res) {
  res.cookie('id', '1', { httpOnly: true });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { httpOnly: true } });
}

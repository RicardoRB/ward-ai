export function login(res) {
  res.cookie('id', '1', { httpOnly: false });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { secure: false } });
}

export function login(res) {
  res.cookie('id', '1', { secure: true });
}

export function crossSite(res) {
  res.cookie('id', '1', { sameSite: 'none', secure: true });
}

export function sessions(session) {
  return session({ secret: 'x', cookie: { secure: true } });
}

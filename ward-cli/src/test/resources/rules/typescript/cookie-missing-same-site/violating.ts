export function login(res: any) {
  res.cookie('id', '1', { secure: true });
}

export function crossSite(res: any) {
  res.cookie('id', '1', { sameSite: 'none', secure: true });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { secure: true } });
}

export function name(user: { profile?: { name: string } }): unknown {
  return user && user.profile;
}

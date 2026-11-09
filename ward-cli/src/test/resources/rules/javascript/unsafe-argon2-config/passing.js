export async function store(password) {
  const hash = await argon2.hash(password, { type: argon2.argon2id });
  return hash;
}

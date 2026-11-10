export async function store(password) {
  const hash = await argon2.hash(password);
  return hash;
}

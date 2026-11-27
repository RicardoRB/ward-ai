// Rows that an ORM reads hold what users stored, so a value of a row is untrusted (possible).
export async function storedName(req, res) {
  const user = await User.findOne({ where: { id: 1 } });
  res.send(user.name);
}

export async function prismaRows(req, res) {
  const rows = await prisma.user.findMany();
  res.send(rows[0].bio);
}

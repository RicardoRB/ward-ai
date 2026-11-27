import escape from 'escape-html';
// A lookup that is not an ORM read gives program data: Array.find and a literal table.
export async function programRows(req, res) {
  const rows = [{ name: 'a' }];
  const found = rows.find((row) => row.name === 'a');
  res.send(found.name);
}

// Stored data is escaped before it reaches the response.
export async function escapedName(req, res) {
  const user = await User.findByPk(1);
  res.send(escape(user.name));
}

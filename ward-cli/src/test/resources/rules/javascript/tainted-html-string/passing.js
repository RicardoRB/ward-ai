export const handler = async (event) => {
  const id = 'id: ' + event.id;
  const list = items.concat(event.records);
  const text = `user ${event.name}`;
  return [id, list, text];
};

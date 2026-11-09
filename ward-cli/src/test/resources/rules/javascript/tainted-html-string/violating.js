export const handler = async (event) => {
  const a = '<p>' + event.body + '</p>';
  const b = `<div>${event.queryStringParameters.name}</div>`;
  const c = '<span>'.concat(event.body);
  return [a, b, c];
};

export function direct(req) {
  return fetch(req.query.url);
}

export async function propagated(req, axios) {
  const host = req.query.host;
  const address = 'http://' + host + '/status';
  return axios.get(address);
}

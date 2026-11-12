export function direct(req: any) {
  return fetch(req.query.url);
}

export async function propagated(req: any, axios: any) {
  const host = req.query.host;
  const address = 'http://' + host + '/status';
  return axios.get(address);
}

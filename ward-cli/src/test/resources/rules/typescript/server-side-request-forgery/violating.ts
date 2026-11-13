export function direct(req: any) {
  return fetch(req.query.url);
}

export async function propagated(req: any, axios: any) {
  const host = req.query.host;
  const address = 'http://' + host + '/status';
  return axios.get(address);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any) {
  const address = read(req);
  return fetch(address);
}

function read(req: any) {
  return req.query.url;
}

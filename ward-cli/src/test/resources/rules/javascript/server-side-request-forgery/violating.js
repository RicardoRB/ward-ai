export function direct(req) {
  return fetch(req.query.url);
}

export async function propagated(req, axios) {
  const host = req.query.host;
  const address = 'http://' + host + '/status';
  return axios.get(address);
}

// A value that another function of the file returns is followed.
export function crossFunction(req) {
  const address = read(req);
  return fetch(address);
}

function read(req) {
  return req.query.url;
}

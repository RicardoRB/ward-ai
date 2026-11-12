export function constant() {
  return fetch('https://api.example.com/status');
}

export function numeric(req) {
  const id = Number(req.query.id);
  return fetch('https://api.example.com/items/' + id);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req) {
  const address = read(req);
  return fetch(address);
}

function read(req) {
  return req.query.url;
}

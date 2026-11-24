export function load() {
  return fetch('https://api.example.com/v1/items');
}

export function local() {
  return fetch('http://localhost:3000/health');
}

export function socket() {
  return new WebSocket('wss://feed.example.com/live');
}

export function withCredentials(port: number) {
  return fetch(`http://user:secret@localhost:${port}/health`);
}

// A request to the address that a server reports for itself stays on the machine.
export function ownAddress(server: any) {
  const address = server.address() as { address: string; family: string; port: number };
  const host = address.family === 'IPv6' ? `[${address.address}]` : address.address;
  return fetch(`http://${host}:${address.port}/`);
}

export function direct(server: any) {
  return fetch(`http://${server.address().address}/x`);
}

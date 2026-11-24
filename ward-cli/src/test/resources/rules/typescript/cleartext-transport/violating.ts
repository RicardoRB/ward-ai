export function load() {
  return fetch('http://api.example.com/v1/items');
}

export function socket() {
  return new WebSocket('ws://feed.example.com/live');
}

export function built(host) {
  return fetch(`http://${host}/x`);
}

export function otherHost(server: any, other: string) {
  let host = server.address().address;
  host = other;
  return fetch(`http://${host}/x`);
}

export function member(config: any) {
  return fetch(`http://${config.address}/x`);
}

export function suffix(server: any) {
  return fetch(`http://${server.address().address}.example.com/x`);
}

export function literalHost(id: number) {
  return fetch(`http://api.example.com:8080/items/${id}`);
}

export function pathOnly(server: any, port: string) {
  return fetch(`http://api.example.com:${port}/x`);
}

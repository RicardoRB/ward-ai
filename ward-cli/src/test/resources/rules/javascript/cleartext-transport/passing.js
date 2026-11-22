export function load() {
  return fetch('https://api.example.com/v1/items');
}

export function local() {
  return fetch('http://localhost:3000/health');
}

export function socket() {
  return new WebSocket('wss://feed.example.com/live');
}

export function builtAddress(host, port) {
  return fetch(`http://${host}:${port}/health`);
}

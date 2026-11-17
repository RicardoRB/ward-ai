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

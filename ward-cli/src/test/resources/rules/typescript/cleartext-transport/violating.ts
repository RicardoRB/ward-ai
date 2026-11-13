export function load() {
  return fetch('http://api.example.com/v1/items');
}

export function socket() {
  return new WebSocket('ws://feed.example.com/live');
}

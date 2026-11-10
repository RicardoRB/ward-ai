export function notify(win) {
  win.postMessage({ ready: true }, 'https://example.com');
}

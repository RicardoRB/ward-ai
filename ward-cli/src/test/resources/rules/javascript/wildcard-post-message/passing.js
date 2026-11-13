export function send(frame, data) {
  frame.contentWindow.postMessage(data, 'https://widget.example.com');
}

export function send(frame: any, data: any) {
  frame.contentWindow.postMessage(data, '*');
}

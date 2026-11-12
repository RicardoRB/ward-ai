export function show(element: any, text: any) {
  element.textContent = text;
  element.innerHTML = '<b>fixed</b>';
}

export function write() {
  document.write('<hr>');
}

export function show(element, text) {
  element.textContent = text;
  element.innerHTML = '<b>fixed</b>';
}

export function write() {
  document.write('<hr>');
}

export function show(element: any, html: any) {
  element.innerHTML = html;
}

export function write(markup: any) {
  document.write(markup);
}

export function insert(element: any, markup: any) {
  element.insertAdjacentHTML('beforeend', markup);
}

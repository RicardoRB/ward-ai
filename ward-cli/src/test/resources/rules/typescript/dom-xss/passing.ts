export function show(element: any, text: any) {
  element.textContent = text;
  element.innerHTML = '<b>fixed</b>';
}

export function write() {
  document.write('<hr>');
}

export function same(element: any, html: any) {
  return element.innerHTML === html;
}

// A message built from constants and numbers holds no markup, whatever the numbers are.
export function progress(loaded: number, total: number, element: any) {
  let message = 'Loaded ';
  if (total) {
    message += (100 * loaded / total).toFixed(0) + '%';
  } else {
    message += (loaded / 1024).toFixed(2) + ' KB';
  }
  element.innerHTML = message;
}

export function count(items: string[], element: any) {
  element.innerHTML = '<b>' + items.length + '</b>';
}

export function product(a: string, b: string, element: any) {
  element.innerHTML = `<i>${a * b}</i>`;
}

export function converted(value: string, element: any) {
  element.innerHTML = Number(value);
}

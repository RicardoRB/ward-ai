export function show(element: any, html: any) {
  element.innerHTML = html;
}

export function write(markup: any) {
  document.write(markup);
}

export function insert(element: any, markup: any) {
  element.insertAdjacentHTML('beforeend', markup);
}

// One text that an attacker chose is enough, on one path or after a reassignment.
export function appended(progress: any, element: any) {
  let message = 'Loaded ';
  message += progress.text;
  element.innerHTML = message;
}

export function reassigned(element: any, text: string) {
  let message = 'n: ' + 1;
  message = text;
  element.innerHTML = message;
}

export function onePath(element: any, text: string, flag: boolean) {
  let message = '<b>x</b>';
  if (flag) {
    message = text;
  }
  element.innerHTML = message;
}

export function joined(element: any, name: string) {
  element.innerHTML = '<b>' + name + '</b>';
}

export function converted(element: any, name: string) {
  element.innerHTML = name.toUpperCase();
}

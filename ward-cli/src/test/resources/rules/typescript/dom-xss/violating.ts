import snarkdown from 'snarkdown';

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

const HINTS = [{ text: 'fixed' }];

// An exported function can be called from anywhere, so its parameter is not program data.
export function exportedRender(hint: { text: string }, element: any) {
  element.innerHTML = snarkdown(hint.text);
}

// A function passed as a value is called from places the file does not show.
function callbackRender(hint: { text: string }) {
  document.body.innerHTML = snarkdown(hint.text);
}
HINTS.forEach(callbackRender);

// One caller that passes request data is enough.
function twoCallers(hint: { text: string }, element: any) {
  element.innerHTML = snarkdown(hint.text);
}
export function fromConstants(element: any) {
  for (const hint of HINTS) {
    twoCallers(hint, element);
  }
}
export function fromRequest(request: any, element: any) {
  twoCallers({ text: request.query.q }, element);
}

// A constant array that code fills with request data is not program data.
const FILLED = [{ text: 'fixed' }];
function filledRender(hint: { text: string }, element: any) {
  element.innerHTML = snarkdown(hint.text);
}
export function fill(request: any) {
  FILLED.push({ text: request.query.q });
}
export function runFilled(element: any) {
  for (const hint of FILLED) {
    filledRender(hint, element);
  }
}

const CHANGED = [{ text: 'fixed' }];
function changedRender(hint: { text: string }, element: any) {
  element.innerHTML = snarkdown(hint.text);
}
export function change(request: any) {
  CHANGED[0].text = request.query.q;
}
export function runChanged(element: any) {
  for (const hint of CHANGED) {
    changedRender(hint, element);
  }
}

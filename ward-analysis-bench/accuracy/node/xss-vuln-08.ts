function show(el: any, v: string) {
  el.innerHTML = v;
}
export function f(el: any) {
  show(el, location.search);
}

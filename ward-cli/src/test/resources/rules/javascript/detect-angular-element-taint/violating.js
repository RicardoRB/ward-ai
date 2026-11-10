export function show() {
  const text = location.search;
  angular.element(document.body).html(text);
}

function foo(myVar) {
  console.log('foo');
}
new Promise((accept, reject) => {
  window.setTimeout(accept, 1000);
});

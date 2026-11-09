Promise.resolve(1).finally(() => { console.log(2) });
myPromise.finally(function () {
  function nested() {
    return 2;
  }
  console.log(nested());
});

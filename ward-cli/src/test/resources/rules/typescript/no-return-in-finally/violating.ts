Promise.resolve(1).finally(() => { return 2 });
myPromise.finally(() => {
  if (condition) {
    return 2;
  }
});

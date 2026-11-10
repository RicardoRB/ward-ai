function run(n) {
  outer: for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      if (j === i) {
        continue outer;
      }
      if (j > 5) {
        break outer;
      }
      work(i, j);
    }
  }
  done();
}

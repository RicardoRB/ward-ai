function run(n: number): void {
  switch (n) {
    case 1:
      one();
      break;
    default:
      other();
  }
  done();
}

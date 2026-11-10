let flag: boolean;

function run(a: boolean): void {
  if (a && check()) {
    work();
  }
  flag = a || check();
  done();
}

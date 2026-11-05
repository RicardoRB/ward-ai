let flag;

function run(a) {
  if (a && check()) {
    work();
  }
  flag = a || check();
  done();
}

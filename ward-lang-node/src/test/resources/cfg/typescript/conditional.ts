let result: string;

function run(a: boolean): void {
  result = a ? one() : two();
  done();
}

function foo(myVar) {
  console.log(myVar);
}
function withObjectSpread({ a, ...rest }) {
  return rest;
}
function cancelAll(jobs: Array<{cancel?: (reason: Error) => void}>, reason: Error): void {
  for (const job of jobs) {
    job.cancel?.(reason);
  }
}
const listen = (onConnection?: (socket: object) => void) => (socket: object) => {
  onConnection?.(socket);
};
console.log(cancelAll, listen);

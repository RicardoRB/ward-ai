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

function parse(text: string): number;
function parse(text: string, radix: number): number;
function parse(text: string, radix?: number): number {
  return Number.parseInt(text, radix);
}

export const create = <T extends object>(Factory: new (options: object) => T): {value: T} => {
  const value: T = new Factory({});
  return {value};
};

export {parse};

declare function clear(timeout: number): void;
export const install = (target: {clearTimeout?: (timeout: number) => void}) => {
  target.clearTimeout = (timeout: number) => {
    if (timeout) {
      clear(timeout);
    }
  };
};

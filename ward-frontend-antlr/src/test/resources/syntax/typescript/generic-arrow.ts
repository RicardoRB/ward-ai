declare function run(task: unknown): void;

const identity = <T>(value: T) => value;
run(<T>(value: T) => value);
run(async <T, R>(value: T, other?: R) => value);
const all = (async <T>(values: T[]) => values) as unknown;
const count = <number>identity(1);

console.log(all, count);

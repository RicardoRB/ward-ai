const value: unknown = 4;
const n = <number>value;
const list = <string[]>(<unknown>["a"]);
const less = n < 3 && list.length > 1;

declare const obj: {a: number};
declare const list: number[];
const {a} = obj;
const [first] = list;
const empty = {};
const none: number[] = [];
function f({b}: {b: number}): void {}
function g([c]: number[]): void {}

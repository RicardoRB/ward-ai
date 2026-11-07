declare const obj: object;
declare const list: number[];
const {} = obj;
const [] = list;
function f({}: object): void {}
function g([]: number[]): void {}

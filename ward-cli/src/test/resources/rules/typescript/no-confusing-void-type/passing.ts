function r1(): void {}
let r2: Promise<void>;
let r3: () => void;
function r4<T = void>(z: T) {}
let r5: T extends void ? string : number;
function r6(this: void): void {}
const obj2 = { m(): void {} };

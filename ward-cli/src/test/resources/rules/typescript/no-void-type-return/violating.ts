function f1(): void { return 1; }
function f2(): void { return; }
function f3(): void { return void 1; }
class C1 { m(): void { return 2; } }
const a1 = (): void => { return 3; };
const b1 = function (): void { return 4; };
function f4(): void { if (x) { return 5; } }
function f5(): number { return 6; }
function f6(): void { const f = () => { return 7; }; }
function f7(): void | number { return 9; }

const arrow = (x, { y, z: renamed = 1 }, [first, ...others]) => x + y + renamed + first + others;
const named = function inner(n) { return n ? inner(n - 1) : 0; };
const single = v => v;
try {
  risky();
} catch (error) {
  console.log(error);
}
for (const item of list) { item; }
for (let i = 0; i < 3; i++) { i; }
for (key in object) {}
switch (kind) { case 1: let scoped = 1; break; default: scoped; }
const obj = { key: value, short, [computed]: 1, method() { return key; }, get prop() { return this.key; } };
obj.member.deep = obj.other;
label: for (;;) { break label; }
counter += 1;
[swapA, swapB] = [swapB, swapA];

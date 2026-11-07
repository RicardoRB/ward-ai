let a = 0;
let b = 0;
for (let i = 0, j = 0; i < 1; i++, j++) {}
a = (b++, 2);
function f() {
  return (a, b);
}

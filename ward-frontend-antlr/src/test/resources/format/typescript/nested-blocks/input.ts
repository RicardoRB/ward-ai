function outer(items: string[]): number {
let total = 0;
for (const item of items) {
  if (item.length > 3) {
    total += item.length;
  } else if (item === "") {
  total -= 1;
  } else {
    total++;
  }
}
switch (total) {
case 0: return 0;
default:
  break;
}
try { risky(); } catch (e) { log(e); } finally { done(); }
return total;
}

console.log(hoisted, later, tdz);
var hoisted = 1;
let later = 2;
function declared(a, b = a, ...rest) {
  var inner = 1;
  if (a) {
    var leaked = 2;
    let scoped = 3;
    const fixed = scoped;
  }
  return leaked + inner + missing;
}
{
  tdz;
  let tdz = 1;
}
function early() { return late(); }
function late() { return 1; }
var again = 1;
var again = 2;

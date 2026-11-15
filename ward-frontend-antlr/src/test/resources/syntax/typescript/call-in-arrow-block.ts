const one = () => { return g(x) }
const two = () => { return g(1) }
const three = () => { return g() }
const four = () => { return g(x, y) }
const five = (a: number) => {
  return g(a)
}
function six() { return g(x) }

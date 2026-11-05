const a = 1
const b = a
+ 2
let c = b
(function run() {
  console.log(c)
})
export class Box {
  size = 3
  grow() {
    this.size++
  }
}

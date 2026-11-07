func compute(a: Int, b: Int) -> Int {
    let x = a + b * 2 - a / 3 + b % 4 - a * b + 7
    let y = x * x - a + b / 2 + x % 5 - 9
    let z = y + x - a * 3 + b / 4 - x * 2 + 1
    let w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5
    let v = w - z + y * 2 - x + a / 5 + b * 4 - 6
    let u = v * 2 - w + z / 3 - y + x % 4 + a - b
    let x = a + b * 2 - a / 3 + b % 4 - a * b + 7
    let y = x * x - a + b / 2 + x % 5 - 9
    let z = y + x - a * 3 + b / 4 - x * 2 + 1
    let w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5
    let v = w - z + y * 2 - x + a / 5 + b * 4 - 6
    let u = v * 2 - w + z / 3 - y + x % 4 + a - b
    return x + y + z + w + v + u
}

pub fn differ(x: i32, y: i32) -> bool {
    let a = x == y;
    let b = y - x;
    a && b == 0
}

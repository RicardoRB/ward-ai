pub fn same(x: i32, y: i32) -> bool {
    let a = x == x;
    let b = y - y;
    a && b == 0
}

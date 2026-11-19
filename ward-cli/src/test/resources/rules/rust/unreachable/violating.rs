pub fn never(x: i32) -> i32 {
    match x {
        0 => 1,
        _ => unreachable!(),
    }
}

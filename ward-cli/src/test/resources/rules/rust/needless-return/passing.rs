pub fn five() -> i32 {
    5
}

pub fn early(x: i32) -> i32 {
    if x > 1 {
        return 1;
    }
    2
}

pub fn gated() -> i32 {
    return 1;
    panic!();
}

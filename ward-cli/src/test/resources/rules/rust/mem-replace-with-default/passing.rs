pub fn grab(v: &mut Vec<i32>) -> Vec<i32> {
    std::mem::take(v)
}

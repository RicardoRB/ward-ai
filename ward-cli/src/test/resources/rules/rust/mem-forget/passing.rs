pub fn leak(v: Vec<i32>) {
    drop(v)
}

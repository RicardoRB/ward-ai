pub fn has_big(v: &[i32]) -> bool {
    v.iter().any(|x| *x > 1)
}

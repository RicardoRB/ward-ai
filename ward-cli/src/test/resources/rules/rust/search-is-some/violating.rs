pub fn has_big(v: &[i32]) -> bool {
    v.iter().find(|x| **x > 1).is_some()
}

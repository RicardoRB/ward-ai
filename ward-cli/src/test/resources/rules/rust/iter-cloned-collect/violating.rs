pub fn copy(v: &[i32]) -> Vec<i32> {
    v.iter().cloned().collect::<Vec<_>>()
}

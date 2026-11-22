pub fn all(v: &[i32]) -> Vec<i32> {
    v.iter().flat_map(|x| Some(*x)).collect()
}

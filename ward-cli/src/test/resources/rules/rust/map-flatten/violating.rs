pub fn all(v: &[i32]) -> Vec<i32> {
    v.iter().map(|x| Some(*x)).flatten().collect()
}

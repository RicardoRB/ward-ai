fn double(x: &i32) -> i32 {
    *x * 2
}

pub fn all(v: &[i32]) -> Vec<i32> {
    v.iter().map(double).collect()
}

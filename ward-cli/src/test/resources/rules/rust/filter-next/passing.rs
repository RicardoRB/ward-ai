pub fn first_big(v: &[i32]) -> Option<&i32> {
    v.iter().find(|x| **x > 1)
}

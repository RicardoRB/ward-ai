pub fn empty(v: &[i32]) -> bool {
    v.is_empty()
}

pub struct Bag(Vec<i32>);

impl Bag {
    pub fn len(&self) -> usize {
        self.0.len()
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }
}

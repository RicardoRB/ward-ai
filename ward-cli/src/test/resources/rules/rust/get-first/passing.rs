pub fn head(v: &[i32]) -> Option<&i32> {
    v.first()
}

pub struct Groups(Vec<String>);

impl Groups {
    pub fn get(&self, index: usize) -> Option<&String> {
        self.0.get(index)
    }

    pub fn whole(&self) -> &String {
        self.get(0).unwrap()
    }
}

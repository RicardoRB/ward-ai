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

pub fn lookups(by_id: HashMap<u32, String>, groups: &Groups) -> usize {
    let found = by_id.get(0);
    let own = groups.get(0);
    let table = Groups::new();
    table.get(0);
    found.map_or(0, |s| s.len()) + own.map_or(0, |s| s.len())
}

pub trait Lookup {
    fn get(&self, index: usize) -> Option<u32>;
}

pub fn through_trait(found: &Lookup) -> Option<u32> {
    found.get(0)
}

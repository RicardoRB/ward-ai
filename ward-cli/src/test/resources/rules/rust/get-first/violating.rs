pub fn head(v: &[i32]) -> Option<&i32> {
    v.get(0)
}

pub struct Groups(Vec<String>);

impl Groups {
    pub fn get(&self, index: usize) -> Option<&String> {
        self.0.get(index)
    }

    pub fn first_group(&self) -> Option<&String> {
        self.0.get(0)
    }
}

pub struct Table {
    rows: VecDeque<u32>,
}

impl Table {
    pub fn get(&self, key: u32) -> u32 {
        key
    }

    pub fn first_row(&self) -> Option<&u32> {
        self.rows.get(0)
    }
}

pub fn locals(names: Vec<String>, flags: &mut [bool]) -> usize {
    let made = vec![1, 2];
    let queue: VecDeque<u8> = VecDeque::new();
    let fresh = Vec::new();
    let _ = (made.get(0), queue.get(0), fresh.get(0), flags.get(0));
    names.get(0).map_or(0, |n| n.len())
}

pub struct Tokens(Vec<u8>);

pub struct Glob {
    tokens: Tokens,
}

impl Glob {
    pub fn lead(&self) -> Option<&u8> {
        self.tokens.get(0)
    }
}

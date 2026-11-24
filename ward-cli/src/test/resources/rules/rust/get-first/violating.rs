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

pub type Tokens = Vec<u8>;

pub struct Glob {
    tokens: Tokens,
}

impl Glob {
    pub fn lead(&self) -> Option<&u8> {
        self.tokens.get(0)
    }
}

pub fn names() -> Result<Vec<String>, Error> {
    Ok(Vec::new())
}

pub fn through_return() -> Option<String> {
    let all = names().unwrap();
    let first = all.get(0);
    first.cloned()
}

pub struct Rows {
    cells: Vec<Vec<u8>>,
}

impl Rows {
    pub fn row(&self) -> Result<Vec<u8>, Error> {
        Ok(Vec::new())
    }

    pub fn lead(&self) -> Option<u8> {
        let row = self.row().ok()?;
        let by_method = self.row().unwrap();
        by_method.get(0).copied()
    }

    pub fn each(&self) -> usize {
        self.cells.iter().map(|cell| cell.len()).sum()
    }
}

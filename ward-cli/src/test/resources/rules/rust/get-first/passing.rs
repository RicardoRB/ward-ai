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

pub trait Matcher {
    type Captures: Captures;

    fn new_captures(&self) -> Result<Self::Captures, Error>;
}

pub trait Captures {
    fn get(&self, index: usize) -> Option<Span>;
}

pub struct Unit;

impl Matcher for Unit {
    type Captures = Slots;

    fn new_captures(&self) -> Result<Slots, Error> {
        Ok(Slots::new())
    }
}

pub fn first_slot<M: Matcher>(matcher: &M) -> Option<Span> {
    let caps = matcher.new_captures().unwrap();
    caps.get(0)
}

pub fn through_question(matcher: &Unit) -> Result<Option<Span>, Error> {
    let caps = matcher.new_captures()?;
    Ok(caps.get(0))
}

pub fn unknown(source: Source) -> Option<u8> {
    let found = source.load();
    found.get(0).copied()
}

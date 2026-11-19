use std::ops::Add;

pub struct Meters(pub f64);

impl Add for Meters {
    type Output = Meters;

    fn add(self, other: Meters) -> Meters {
        Meters(self.0 + other.0)
    }
}

unsafe impl Send for Meters {}
impl !Sync for Meters {}

impl<T> Default for Box<Wrapper<T>> where T: Default {
    fn default() -> Self {
        Box::new(Wrapper(T::default()))
    }
}

pub struct Wrapper<T>(T);

fn qualified() {
    let value = <Meters as Add>::add(Meters(1.0), Meters(2.0));
    let default = <Vec<u8>>::default();
    let _ = (value, default, <u8 as Default>::default());
}

fn fn_pointers(callback: fn(i32) -> i32, unsafe_callback: unsafe extern "C" fn(*const u8)) {
    let _ = (callback, unsafe_callback);
}

fn never() -> ! {
    loop {}
}

fn slices(data: &[u8], text: &str) -> usize {
    let [head, rest @ ..] = data else { return 0 };
    let part = &text[1..];
    let _ = (head, rest, part);
    data.len()
}

fn question(input: Option<&str>) -> Result<usize, Box<dyn std::error::Error>> {
    let text = input.ok_or("missing")?;
    let number: usize = text.parse()?;
    Ok(number)
}

fn raw_names() {
    let r#type = 1;
    let r#match = r#type + 1;
    let _ = r#match;
}

pub fn returns_tuple() -> (impl Fn(), u8) {
    (|| (), 1)
}

pub struct Packed<const N: usize = 4> {
    data: [u8; N],
}

fn const_generic_call() {
    let packed: Packed<{ 2 + 2 }> = Packed { data: [0; 4] };
    let _ = packed;
}

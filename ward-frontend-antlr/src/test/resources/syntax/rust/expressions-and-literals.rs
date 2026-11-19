fn literals() {
    let integers = (1, 1_000u64, 0xff, 0o77, 0b1010_1010u8, 7usize);
    let floats = (1.0, 2.5e10, 3e-2f32, 1., 1_0.0_1f64);
    let text = ("plain", "multi
line", "esc\n\t\"\\\u{1F600}\x41", r"raw", r#"raw "quoted""#, "continued \
        line");
    let bytes = (b'a', b"bytes", br"raw bytes", br#"x"#, b'\n');
    let chars = ('a', '\'', '\u{41}', '\\');
    let c_strings = (c"nul terminated", cr"raw c");
    let tuple_index = ((1, (2, 3)), 4);
    let _ = tuple_index.0.1.0 + tuple_index.1;
    let _ = (integers, floats, text, bytes, chars, c_strings);
}

fn operators(a: i32, b: i32) -> bool {
    let shifted = (a << 2) >> 1;
    let mixed = a as i64 + b as i64 * 2 - 3 % 2;
    let flags = a & b | a ^ b;
    let mut total = 0;
    total += a;
    total -= b;
    total *= 2;
    total /= 2;
    total %= 7;
    total <<= 1;
    total >>= 1;
    total &= 0xf;
    total |= 1;
    total ^= 2;
    let range = (0..3, 0..=3, ..3, 3.., ..);
    let negated = -a + !b;
    let reference = &&a;
    let deref = **reference;
    let _ = (shifted, mixed, flags, range, negated, deref);
    a < b && b > a || a <= b && !(a >= b) || a == b || a != b
}

fn chained(items: &[i32]) -> Option<i32> {
    let value = items.iter().map(|x| x + 1).filter(|x| x % 2 == 0).max()?;
    let turbofish = "5".parse::<i32>().unwrap_or_default();
    let array = [0u8; 4];
    let nested: Vec<Vec<i32>> = vec![vec![1], vec![]];
    let cast = value as u8 as char;
    let tuple = (1,);
    let unit = ();
    let _ = (turbofish, array, nested, cast, tuple, unit);
    Some(value)
}

struct Point { x: i32, y: i32 }

fn structs() -> Point {
    let x = 1;
    let base = Point { x: 0, y: 0 };
    let moved = Point { x, ..base };
    Point { y: 2, ..moved }
}

fn raw_refs(value: &mut u32) {
    let raw = &raw mut *value;
    let const_raw = &raw const *value;
    let _ = (raw, const_raw);
    let block = const { 1 + 2 };
    let _ = block;
}

enum Shape {
    Circle { radius: f64 },
    Rect(f64, f64),
    Empty,
}

fn parse(input: &str) -> Option<u32> {
    let Some(first) = input.split(',').next() else {
        return None;
    };
    let Ok(number) = first.trim().parse::<u32>() else { return None };
    Some(number)
}

fn area(shape: &Shape) -> f64 {
    match shape {
        Shape::Circle { radius } if *radius > 0.0 => 3.14 * radius * radius,
        Shape::Circle { .. } => 0.0,
        Shape::Rect(w, h) | Shape::Rect(h, w) if w == h => w * w,
        Shape::Rect(w, h) => w * h,
        Shape::Empty => 0.0,
    }
}

fn patterns(value: (i32, [u8; 3], &str)) {
    let (a, [first, .., last], name @ ("x" | "y")) = value else {
        return;
    };
    let n = 5;
    let kind = match n {
        0 => "zero",
        1..=9 => "digit",
        10.. => "big",
        i32::MIN..=-1 => "negative",
    };
    if let Some(x) = Some(a) && x > 0 && let Some(y) = Some(first) {
        println!("{x} {y} {last} {name} {kind}");
    }
    while let Some(top) = Some(1) {
        break;
    }
    let ref borrowed = n;
    let &copied = &n;
    let Shape::Circle { radius: r, .. } = (Shape::Circle { radius: 1.0 }) else { return };
    let _ = (borrowed, copied, r);
}

fn labeled() {
    let mut count = 0;
    'outer: loop {
        'inner: for i in 0..10 {
            if i == 3 { continue 'outer; }
            if i == 5 { break 'inner; }
            count += i;
        }
        if count > 10 { break 'outer; }
    }
    let value = 'found: {
        if count > 3 { break 'found 1; }
        2
    };
    let _ = value;
}

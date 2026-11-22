use std::fs;
use std::fs::File;
use std::path::Path;

fn show() {
    let name = std::env::args().nth(1).unwrap();
    let text = fs::read_to_string(Path::new("/srv/files").join(&name)).unwrap();
    println!("{text}");
}

fn open() {
    let file = std::env::var("FILE").unwrap();
    File::open(format!("/srv/data/{}", file)).unwrap();
}

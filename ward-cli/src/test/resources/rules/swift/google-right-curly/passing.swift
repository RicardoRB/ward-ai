func check(flag: Bool) {
    if flag {
        print("a")
    } else {
        print("b")
    }
}

func load(first: Int?, second: Int?) {
    guard let first,
          let second
    else { return }
    print(first + second)
}

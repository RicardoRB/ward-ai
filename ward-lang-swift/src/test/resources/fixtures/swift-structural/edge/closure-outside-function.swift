func outer(value: Int) -> Int {
    var total = 0
    if value > 0 { total += 1 }
    if value > 1 { total += 1 }
    if value > 2 { total += 1 }
    if value > 3 { total += 1 }
    if value > 4 { total += 1 }
    if value > 5 { total += 1 }
    if value > 6 { total += 1 }
    if value > 7 { total += 1 }
    if value > 8 { total += 1 }
    let run = { (x: Int) -> Int in
        if x > 0 { return 1 }
        if x > 1 { return 2 }
        if x > 2 { return 3 }
        return 0
    }
    return total + run(value)
}

func choose(value: Int) -> Int {
    var total = 0
    if value > 0 {
        total += 1
    } else {
        total -= 1
    }
    if value > 1 {
        total += 1
    } else {
        total -= 1
    }
    if value > 2 {
        total += 1
    } else {
        total -= 1
    }
    if value > 3 {
        total += 1
    } else {
        total -= 1
    }
    if value > 4 {
        total += 1
    } else {
        total -= 1
    }
    if value > 5 {
        total += 1
    } else {
        total -= 1
    }
    if value > 6 {
        total += 1
    } else {
        total -= 1
    }
    if value > 7 {
        total += 1
    } else {
        total -= 1
    }
    if value > 8 {
        total += 1
    } else {
        total -= 1
    }
    return total
}

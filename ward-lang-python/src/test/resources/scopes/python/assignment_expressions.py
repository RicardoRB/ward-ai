data = [1, 2, 3]
if (count := len(data)) > 2:
    print(count)
squares = [y for x in data if (y := x * x) > 1]
after = y
def collect(items):
    kept = [z for x in items if (z := x)]
    return kept, z

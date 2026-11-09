counter = 0


def bump():
    print(counter)
    global counter
    counter = 2

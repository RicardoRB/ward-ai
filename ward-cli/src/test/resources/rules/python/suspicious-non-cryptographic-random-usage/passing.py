secrets.randbelow(10)


def seed():
    return random.randint(1, 10)


def next_report(day):
    delay = random.randint(0, 59)
    return day + delay

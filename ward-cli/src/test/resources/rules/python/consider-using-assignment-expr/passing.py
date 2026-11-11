def run(find):
    if match := find():
        return match
    value = find()
    print(value)
    if value:
        return value
    return None

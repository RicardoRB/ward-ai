def pair(first, *rest):
    return *rest, first

def walk(*groups):
    for item in *groups, ():
        print(item)

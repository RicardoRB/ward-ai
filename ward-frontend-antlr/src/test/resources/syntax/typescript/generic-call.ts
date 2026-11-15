class Store {
  public readonly items = signal<Item[]>([])
  public readonly emit = output<number | undefined>()
  private data = inject<{
    first: Item;
}>(TOKEN)
}

const a = fetch<Item[]>(url, 1)
const b = pipe<A, B>(x)
const c = handler<(input: string) => void>(callback)
const d = i < n && m > (k)
for (let i = 0; i < n; i++) { run(i) }
if (a < b) { run() } else if (c > (e)) { stop() }

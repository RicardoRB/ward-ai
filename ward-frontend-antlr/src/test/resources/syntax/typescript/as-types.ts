async function load() {
  return await fetchAll('users') as User[]
}

const a = value as Item[]
const b = names as unknown as [First, ...Rest[]]
const c = list as readonly string[]
const d = config as const
const e = (response as Response<Item>).body
const f = flag ? raw as { data?: { role?: string } } : undefined
const g = first as number | string

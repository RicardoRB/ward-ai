export interface Account {
  id?: number
  name: string
  tags: string[]
  run(): void
  readonly kind: 'a' | 'b'
}

const read = (value: { first: string, second: number }) => value.first

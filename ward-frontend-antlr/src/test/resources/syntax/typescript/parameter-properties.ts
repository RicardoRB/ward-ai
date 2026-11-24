class Service {
  constructor(
    public readonly settings = { enabled: true },
    @Inject('TOKEN') readonly missing?: unknown,
    private readonly connection: Connection,
  ) {}
}
class Other {
  constructor(
    protected readonly limit: number = 3,
    @Optional() private readonly extra?: string,
    public name?: string,
  ) {}
}
class Many {
  constructor(
    @Inject(CoreService)
    @Optional()
    public readonly core = { default: true },
    @Optional() @Inject('MISSING') readonly missing?: unknown,
    @A() @B() private readonly both: number,
  ) {}
}

export class Holder {
  private name: string = '';

  setName(name: string): void {
    this.name = name;
  }

  rename(name: string): void {
    this.name = name;
  }
}

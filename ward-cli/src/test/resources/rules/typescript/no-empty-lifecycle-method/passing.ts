import { Component } from '@angular/core';

@Component({ selector: 'app-b', template: '' })
export class BComponent {
  ngOnInit(): void {
    this.load();
  }
  ngOnDestroy() {
    this.cleanup();
  }
  private load(): void {}
  private cleanup(): void {}
}

export class Plain {
  ngOnInit(): void {}
}

import { Component } from '@angular/core';

@Component({ selector: 'app-m', template: '' })
export class MComponent {
  ngOnInit(): void {
    this.load();
  }
  ngOnDestroy(): void {
    this.cleanup();
  }
  private load(): void {}
  private cleanup(): void {}
}

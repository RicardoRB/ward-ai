import { Component, OnDestroy, OnInit } from '@angular/core';

@Component({ selector: 'app-m', template: '' })
export class MComponent implements OnInit, OnDestroy {
  ngOnInit(): void {
    this.load();
  }
  ngOnDestroy(): void {
    this.cleanup();
  }
  private load(): void {}
  private cleanup(): void {}
}

export class Plain {
  ngOnInit(): void {}
}

import { TestBed } from '@angular/core/testing';

import Demo from './demo';

describe('Demo', () => {
  let service: Demo;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Demo);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});

import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ToastInfo, ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ToastService],
    });
    service = TestBed.inject(ToastService);
  });

  it('starts with no toasts', () => {
    expect(service.toasts()).toEqual([]);
  });

  it('adds a success toast', () => {
    service.success('This is good', 'Good title');

    expect(service.toasts()).toEqual<ToastInfo[]>([
      {
        message: 'This is good',
        header: 'Good title',
        classname: 'snackbar-success',
        icon: 'check_circle',
      },
    ]);
  });

  it('adds a warning toast', () => {
    service.warning('This is not so good', 'Not so good title');

    expect(service.toasts()).toEqual<ToastInfo[]>([
      {
        message: 'This is not so good',
        header: 'Not so good title',
        classname: 'snackbar-warning',
        icon: 'warning',
      },
    ]);
  });

  it('adds an error toast', () => {
    service.error('This is bad', 'Bad title');

    expect(service.toasts()).toEqual<ToastInfo[]>([
      {
        message: 'This is bad',
        header: 'Bad title',
        classname: 'snackbar-error',
        icon: 'error',
      },
    ]);
  });

  it('appends multiple toasts', () => {
    service.success('Toast 1');
    service.success('Toast 2');

    expect(service.toasts()).toHaveLength(2);
    expect(service.toasts()[0].message).toBe('Toast 1');
    expect(service.toasts()[1].message).toBe('Toast 2');
  });
});

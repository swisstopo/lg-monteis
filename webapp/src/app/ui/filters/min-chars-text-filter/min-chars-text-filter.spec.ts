import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { IFloatingFilterParams, TextFilterModel } from 'ag-grid-community';
import { expect, vi } from 'vitest';
import { MinCharsTextFilter } from './min-chars-text-filter';

describe('MinCharsTextFilter', () => {
  let fixture: ComponentFixture<MinCharsTextFilter>;
  let component: MinCharsTextFilter;
  let onFloatingFilterChanged: ReturnType<typeof vi.fn>;
  let appliedModel: TextFilterModel | null;

  function type(text: string): void {
    fixture.nativeElement.querySelector('input').value = text;
    fixture.nativeElement.querySelector('input').dispatchEvent(new Event('input'));
  }

  beforeEach(async () => {
    appliedModel = null;
    onFloatingFilterChanged = vi.fn();

    await TestBed.configureTestingModule({
      imports: [MinCharsTextFilter],
      providers: [provideTranslateService()],
    }).compileComponents();

    fixture = TestBed.createComponent(MinCharsTextFilter);
    component = fixture.componentInstance;
    component.agInit({
      currentParentModel: () => appliedModel,
      parentFilterInstance: (callback) => callback({ onFloatingFilterChanged } as never),
    } as IFloatingFilterParams);
    fixture.detectChanges();
  });

  it('does not search while the term is shorter than three characters', async () => {
    type('SE');

    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(onFloatingFilterChanged).not.toHaveBeenCalled();
  });

  it('searches the column once three characters are typed', async () => {
    type('SENS');

    await vi.waitFor(() =>
      expect(onFloatingFilterChanged).toHaveBeenCalledWith('contains', 'SENS'),
    );
  });

  it('clears an applied search when the term is shortened below three characters', async () => {
    appliedModel = { filterType: 'text', type: 'contains', filter: 'SENS' };

    type('SE');

    await vi.waitFor(() => expect(onFloatingFilterChanged).toHaveBeenCalledWith('contains', null));
  });

  it('shows the term the grid applied elsewhere, e.g. after the popup was cleared', () => {
    component.onParentModelChanged({ filterType: 'text', type: 'contains', filter: 'TEMP' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input').value).toBe('TEMP');
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ExperimentControllerService,
  NotifyingParameterControllerService,
  NotifyingParameterResponseDto,
  RecipientControllerService,
  RecipientDto,
  RecipientsResponseDto,
  WriteSensorParameterDto,
} from '@core/generated';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import translationsEN from '@public/i18n/en.json';
import { WorkbenchRouter, WorkbenchView } from '@scion/workbench';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import NotificationPage from './notification-page';
import MeasurementStatusEnum = NotifyingParameterResponseDto.MeasurementStatusEnum;

// registered in main.ts, test bed never runs it
ModuleRegistry.registerModules([AllCommunityModule]);

function parameter(
  overrides: Partial<NotifyingParameterResponseDto> = {},
): NotifyingParameterResponseDto {
  return {
    sensorParameterId: 'p1',
    sensorName: 'Piezometer',
    sensorParameterName: 'Pressure',
    dasKey: 'DAS-1',
    unit: WriteSensorParameterDto.UnitEnum.Meter,
    newestMeasurement: '2026-11-17T09:30:00Z',
    measureValue: 12.5,
    sensorActive: true,
    sensorParameterActive: true,
    measurementStatus: MeasurementStatusEnum.Correct,
    ...overrides,
  };
}

const PI_ANNA: RecipientDto = { id: 'anna', firstName: 'Anna', lastName: 'Arnold' };
const PI_BEN: RecipientDto = { id: 'ben', firstName: 'Ben', lastName: 'Berger' };
const READER_CARL: RecipientDto = { id: 'carl', firstName: 'Carl', lastName: 'Christen' };

function recipients(saved: boolean, ...members: RecipientDto[]): RecipientsResponseDto {
  return { saved, recipients: members };
}

describe('NotificationPage', () => {
  let notifyingParameterApi: { findNotifyingParametersOf: ReturnType<typeof vi.fn> };
  let experimentApi: { getExperiment: ReturnType<typeof vi.fn> };
  let recipientApi: { findRecipientsOf: ReturnType<typeof vi.fn> };
  let view: { title: string | undefined };
  let workbenchRouter: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    notifyingParameterApi = { findNotifyingParametersOf: vi.fn(() => of([parameter()])) };
    experimentApi = { getExperiment: vi.fn(() => of({ id: 'x', name: 'Gas Path' })) };
    recipientApi = { findRecipientsOf: vi.fn(() => of(recipients(false, PI_ANNA))) };
    view = { title: undefined };
    workbenchRouter = { navigate: vi.fn(() => Promise.resolve(true)) };
  });

  function render(...parameters: NotifyingParameterResponseDto[]): Promise<PageView> {
    return renderAnswering(of(parameters));
  }

  function unknownExperiment(): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status: 404 }));
  }

  async function renderAnswering(
    response: Observable<NotifyingParameterResponseDto[]>,
  ): Promise<PageView> {
    notifyingParameterApi.findNotifyingParametersOf.mockReturnValue(response);
    TestBed.configureTestingModule({
      imports: [NotificationPage],
      providers: [
        provideTranslateService(),
        { provide: WorkbenchView, useValue: view },
        { provide: WorkbenchRouter, useValue: workbenchRouter },
        { provide: NotifyingParameterControllerService, useValue: notifyingParameterApi },
        { provide: ExperimentControllerService, useValue: experimentApi },
        { provide: RecipientControllerService, useValue: recipientApi },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', translationsEN);
    translate.use('en');
    const fixture = TestBed.createComponent(NotificationPage);
    fixture.componentRef.setInput('experimentId', 'x');
    await fixture.whenStable();
    return new PageView(fixture);
  }

  it('loads the notifying parameters of the experiment in the route', async () => {
    await render(parameter());

    expect(notifyingParameterApi.findNotifyingParametersOf).toHaveBeenCalledWith('x');
  });

  it('titles the tab with the experiment name', async () => {
    await render(parameter());

    await vi.waitFor(() => expect(view.title).toBe('Notifications Gas Path'));
  });

  it('titles the tab without a name until the experiment is known', async () => {
    experimentApi.getExperiment.mockReturnValue(unknownExperiment());

    await render(parameter());

    await vi.waitFor(() => expect(view.title).toBe('Notifications'));
  });

  it('leaves an unknown experiment to the error toast, without an inline error', async () => {
    experimentApi.getExperiment.mockReturnValue(unknownExperiment());

    const page = await renderAnswering(unknownExperiment());

    await vi.waitFor(() => expect(view.title).toBe('Notifications'));
    expect(page.hasInlineError()).toBe(false);
    expect(page.hasTable()).toBe(false);
  });

  it('shows an inline error when the parameters cannot be loaded', async () => {
    const page = await renderAnswering(throwError(() => new HttpErrorResponse({ status: 500 })));

    await vi.waitFor(() => expect(page.hasInlineError()).toBe(true));
  });

  it('names a row by sensor and parameter, or by the DAS key without a parameter name', async () => {
    const page = await render(
      parameter({
        sensorParameterId: 'p1',
        sensorName: 'Piezometer',
        sensorParameterName: 'Pressure',
      }),
      parameter({
        sensorParameterId: 'p2',
        sensorName: 'Piezometer',
        sensorParameterName: undefined,
        dasKey: 'DAS-7',
      }),
    );

    await page.rowsShown(2);
    expect(page.column('sensorParameter')).toEqual(['DAS-7', 'Piezometer – Pressure']);
  });

  it('shows only the column titles in the header, without a filter row', async () => {
    const page = await render(parameter());

    await page.rowsShown(1);
    expect(page.hasFilterRow()).toBe(false);
  });

  it('sorts by sensor parameter ascending by default', async () => {
    const page = await render(
      parameter({ sensorParameterId: 'p1', sensorName: 'Tiltmeter', sensorParameterName: 'X' }),
      parameter({ sensorParameterId: 'p2', sensorName: 'Extensometer', sensorParameterName: 'Y' }),
      parameter({ sensorParameterId: 'p3', sensorName: 'Extensometer', sensorParameterName: 'A' }),
    );

    await page.rowsShown(3);
    expect(page.column('sensorParameter')).toEqual([
      'Extensometer – A',
      'Extensometer – Y',
      'Tiltmeter – X',
    ]);
  });

  it('sorts by sensor parameter regardless of case', async () => {
    const page = await render(
      parameter({ sensorParameterId: 'p1', sensorName: 'beta', sensorParameterName: 'X' }),
      parameter({ sensorParameterId: 'p2', sensorName: 'Gamma', sensorParameterName: 'X' }),
      parameter({ sensorParameterId: 'p3', sensorName: 'Alpha', sensorParameterName: 'X' }),
    );

    await page.rowsShown(3);
    expect(page.column('sensorParameter')).toEqual(['Alpha – X', 'beta – X', 'Gamma – X']);
  });

  it('sorts by last measurement when its header is clicked', async () => {
    const page = await render(
      parameter({
        sensorParameterId: 'p1',
        sensorName: 'A',
        newestMeasurement: '2026-11-17T09:00:00Z',
        measureValue: 3,
      }),
      parameter({
        sensorParameterId: 'p2',
        sensorName: 'B',
        newestMeasurement: '2026-11-15T09:00:00Z',
        measureValue: 1,
      }),
      parameter({
        sensorParameterId: 'p3',
        sensorName: 'C',
        newestMeasurement: '2026-11-16T09:00:00Z',
        measureValue: 2,
      }),
    );
    await page.rowsShown(3);

    await page.sortBy('lastMeasurement');

    expect(page.column('sensorParameter')).toEqual([
      'B – Pressure',
      'C – Pressure',
      'A – Pressure',
    ]);
  });

  it('shows the last measurement with timestamp, value and unit', async () => {
    const page = await render(
      parameter({ newestMeasurement: '2026-11-17T09:30:00Z', measureValue: 12.5 }),
    );

    await page.rowsShown(1);
    expect(page.column('lastMeasurement')[0]).toMatch(/2026.* 12\.50 m$/);
  });

  it('shows - for a sensor parameter without a reading', async () => {
    const page = await render(
      parameter({
        newestMeasurement: undefined,
        measureValue: undefined,
        measurementStatus: undefined,
      }),
    );

    await page.rowsShown(1);
    expect(page.column('lastMeasurement')).toEqual(['-']);
  });

  it('marks a last measurement outside the sensor range as alarm', async () => {
    const page = await render(
      parameter({
        sensorParameterId: 'low',
        sensorName: 'A',
        measurementStatus: MeasurementStatusEnum.TooLow,
      }),
      parameter({
        sensorParameterId: 'ok',
        sensorName: 'B',
        measurementStatus: MeasurementStatusEnum.Correct,
      }),
      parameter({
        sensorParameterId: 'high',
        sensorName: 'C',
        measurementStatus: MeasurementStatusEnum.TooHigh,
      }),
    );

    await page.rowsShown(3);
    expect(page.alarms()).toEqual([true, false, true]);
  });

  it('tags a row inactive when its sensor or its parameter is inactive', async () => {
    const page = await render(
      parameter({ sensorParameterId: 'p1', sensorName: 'A', sensorActive: false }),
      parameter({ sensorParameterId: 'p2', sensorName: 'B', sensorParameterActive: false }),
      parameter({ sensorParameterId: 'p3', sensorName: 'C' }),
    );

    await page.rowsShown(3);
    expect(page.inactiveTags()).toEqual([true, true, false]);
  });

  it('opens the measurements of a DAS key in a new measurement table tab', async () => {
    const page = await render(
      parameter({ sensorParameterId: 'p1', sensorName: 'A', dasKey: 'DAS-1' }),
      parameter({ sensorParameterId: 'p2', sensorName: 'B', dasKey: 'DAS-2' }),
    );
    await page.rowsShown(2);

    page.openDetails(1);

    expect(workbenchRouter.navigate).toHaveBeenCalledWith(
      [
        '/measurements-table',
        {
          filter: JSON.stringify({
            dasKey: { filterType: 'text', type: 'equals', filter: 'DAS-2' },
          }),
        },
      ],
      { target: 'blank' },
    );
  });

  it('disables open details for a sensor parameter without a DAS key', async () => {
    const page = await render(
      parameter({ sensorParameterId: 'p1', sensorParameterName: 'Pressure', dasKey: undefined }),
    );
    await page.rowsShown(1);

    expect(page.openDetailsDisabled(0)).toBe(true);
  });

  it('shows the empty state instead of the table without notifying parameters', async () => {
    const page = await render();

    await vi.waitFor(() =>
      expect(page.text()).toContain('No sensor parameter is Notification active yet'),
    );
    expect(page.hasTable()).toBe(false);
  });

  it('loads the recipients of the experiment in the route', async () => {
    await render(parameter());

    expect(recipientApi.findRecipientsOf).toHaveBeenCalledWith('x', 'body', false, {
      context: expect.anything(),
    });
  });

  it('shows the pis as recipient chips while the list was never saved', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(false, PI_ANNA, PI_BEN)));

    const page = await render(parameter());

    await vi.waitFor(() => expect(page.recipientChips()).toEqual(['Anna Arnold', 'Ben Berger']));
  });

  it('shows the saved recipients as chips', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(true, READER_CARL)));

    const page = await render(parameter());

    await vi.waitFor(() => expect(page.recipientChips()).toEqual(['Carl Christen']));
  });

  it('names a recipient without a name by the e-mail', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(
      of(recipients(true, { id: 'dora', email: 'dora@example.com' })),
    );

    const page = await render(parameter());

    await vi.waitFor(() => expect(page.recipientChips()).toEqual(['dora@example.com']));
  });

  it('shows the recipients below an empty parameter table', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(of(recipients(false, PI_ANNA)));

    const page = await render();

    await vi.waitFor(() => expect(page.recipientChips()).toEqual(['Anna Arnold']));
    expect(page.text()).toContain('No sensor parameter is Notification active yet');
  });

  it('shows an inline error when the recipients cannot be loaded', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    const page = await render(parameter());

    await vi.waitFor(() => expect(page.hasRecipientsError()).toBe(true));
  });

  it('leaves an unknown experiment to the parameter toast, without a recipients error', async () => {
    recipientApi.findRecipientsOf.mockReturnValue(unknownExperiment());

    const page = await renderAnswering(unknownExperiment());

    await vi.waitFor(() => expect(recipientApi.findRecipientsOf).toHaveBeenCalled());
    expect(page.hasRecipientsError()).toBe(false);
  });
});

class PageView {
  private readonly element: HTMLElement;

  constructor(private readonly fixture: ComponentFixture<NotificationPage>) {
    this.element = fixture.nativeElement as HTMLElement;
  }

  async rowsShown(count: number): Promise<void> {
    await vi.waitFor(() => {
      expect(this.rows()).toHaveLength(count);
      // angular cell renderers fill cells a tick after ag-grid adds rows
      expect(this.column('sensorParameter')).not.toContain('');
    });
  }

  column(colId: string): string[] {
    return this.rows().map((row) => this.cell(row, colId).textContent?.trim() ?? '');
  }

  alarms(): boolean[] {
    return this.rows().map((row) =>
      this.cell(row, 'lastMeasurement').classList.contains('measurement-state-alarm'),
    );
  }

  inactiveTags(): boolean[] {
    return this.rows().map(
      (row) => this.cell(row, 'sensorParameter').querySelector('[data-testid="inactive"]') !== null,
    );
  }

  openDetails(rowIndex: number): void {
    this.cell(this.rows()[rowIndex], 'details').querySelector<HTMLElement>('button')!.click();
  }

  openDetailsDisabled(rowIndex: number): boolean {
    return this.cell(this.rows()[rowIndex], 'details').querySelector('button')!.disabled;
  }

  async sortBy(colId: string): Promise<void> {
    const before = this.column('sensorParameter');
    this.element
      .querySelector<HTMLElement>(`.ag-header-cell[col-id="${colId}"] .ag-header-cell-label`)!
      .click();
    await vi.waitFor(() => expect(this.column('sensorParameter')).not.toEqual(before));
  }

  text(): string {
    return this.element.textContent ?? '';
  }

  hasFilterRow(): boolean {
    return this.element.querySelector('.ag-header-row-filter') !== null;
  }

  hasInlineError(): boolean {
    return this.element.querySelector('app-inline-error') !== null;
  }

  recipientChips(): string[] {
    return [
      ...this.element.querySelectorAll<HTMLElement>('[data-testid="recipients"] mat-chip'),
    ].map((chip) => chip.textContent?.trim() ?? '');
  }

  hasRecipientsError(): boolean {
    return this.element.querySelector('[data-testid="recipients"] app-inline-error') !== null;
  }

  hasTable(): boolean {
    return this.element.querySelector('app-table') !== null;
  }

  // ag-grid appends rendered rows in any order, row-index is their display position
  private rows(): HTMLElement[] {
    return [...this.element.querySelectorAll<HTMLElement>('.ag-row[row-index]')].sort(
      (a, b) => Number(a.getAttribute('row-index')) - Number(b.getAttribute('row-index')),
    );
  }

  private cell(row: HTMLElement, colId: string): HTMLElement {
    return row.querySelector<HTMLElement>(`[col-id="${colId}"]`)!;
  }
}

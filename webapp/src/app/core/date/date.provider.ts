import { DATE_PIPE_DEFAULT_OPTIONS } from '@angular/common';
import { EnvironmentProviders, LOCALE_ID, makeEnvironmentProviders } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS } from '@angular/material/core';
import { IsoDateAdapter } from './iso-date.adapter';

export const APP_ISO_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ";

export const APP_DATE_FORMATS = {
  parse: {
    dateInput: 'yyyy-MM-dd',
    // MatTimepicker requires `parse.timeInput`, `display.timeInput` and
    // `display.timeOptionLabel`; `IsoDateAdapter` maps 'HH:mm' to strict 24h format.
    timeInput: 'HH:mm',
  },
  display: {
    dateInput: 'yyyy-MM-dd',
    monthYearLabel: 'MMM yyyy',
    dateA11yLabel: 'LL',
    monthYearA11yLabel: 'MMMM yyyy',
    timeInput: 'HH:mm',
    timeOptionLabel: 'HH:mm',
  },
};

export function provideAppDateConfig(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: LOCALE_ID, useValue: 'en-GB' },
    {
      provide: DATE_PIPE_DEFAULT_OPTIONS,
      useValue: {
        dateFormat: 'yyyy-MM-dd', // Add time tokens here if needed (e.g. 'yyyy-MM-dd HH:mm:ss')
      },
    },
    { provide: DateAdapter, useClass: IsoDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: APP_DATE_FORMATS },
  ]);
}

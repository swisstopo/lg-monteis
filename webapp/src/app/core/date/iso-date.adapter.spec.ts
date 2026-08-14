import { TestBed } from '@angular/core/testing';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { IsoDateAdapter } from './iso-date.adapter';

describe('IsoDateAdapter', () => {
  let adapter: IsoDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        IsoDateAdapter,
        // Provide a default locale just like we do in appConfig
        { provide: MAT_DATE_LOCALE, useValue: 'en-GB' },
      ],
    });
    adapter = TestBed.inject(IsoDateAdapter);
  });

  describe('format()', () => {
    it('should format a valid date as yyyy-MM-dd when displayFormat is exactly "yyyy-MM-dd"', () => {
      // Note: JavaScript Date months are 0-indexed (7 = August)
      const date = new Date(2026, 7, 6);
      const result = adapter.format(date, 'yyyy-MM-dd');
      expect(result).toBe('2026-08-06');
    });

    it('should pad single-digit months and days with a leading zero', () => {
      const date = new Date(2026, 0, 5); // January 5th
      const result = adapter.format(date, 'yyyy-MM-dd');
      expect(result).toBe('2026-01-05');
    });

    it('should return an empty string for null, undefined, or invalid dates', () => {
      expect(adapter.format(null as any, 'yyyy-MM-dd')).toBe('');
      expect(adapter.format(undefined as any, 'yyyy-MM-dd')).toBe('');
      expect(adapter.format(new Date('invalid'), 'yyyy-MM-dd')).toBe('');
    });

    it('should fallback to NativeDateAdapter (super) for other display formats', () => {
      const date = new Date(2026, 7, 6);

      // 'MMM yyyy' should NOT return the forced ISO format
      const result = adapter.format(date, 'MMM yyyy');

      // Result depends on the native locale (en-GB), but it shouldn't be ISO
      expect(result).not.toBe('2026-08-06');
      expect(result).toContain('2026'); // Will likely be 'Aug 2026'
    });

    it('should format time as 24h HH:mm without an AM/PM suffix', () => {
      // 21:30 would render as '9:30 PM' via the native Intl-based implementation
      const date = new Date(2026, 7, 6, 21, 30);

      const result = adapter.format(date, 'HH:mm');

      expect(result).toBe('21:30');
      expect(result).not.toMatch(/[AP]M/i);
    });

    it('should pad single-digit hours and minutes when formatting HH:mm', () => {
      const date = new Date(2026, 7, 6, 9, 5);
      expect(adapter.format(date, 'HH:mm')).toBe('09:05');
    });

    it('should format midnight as 00:00 rather than 12:00 AM', () => {
      const date = new Date(2026, 7, 6, 0, 0);
      expect(adapter.format(date, 'HH:mm')).toBe('00:00');
    });

    it('should include seconds when displayFormat is HH:mm:ss', () => {
      const date = new Date(2026, 7, 6, 23, 59, 7);
      expect(adapter.format(date, 'HH:mm:ss')).toBe('23:59:07');
    });

    it('should return an empty string for invalid dates when formatting time', () => {
      expect(adapter.format(null as any, 'HH:mm')).toBe('');
      expect(adapter.format(new Date('invalid'), 'HH:mm')).toBe('');
    });
  });

  describe('parseTime()', () => {
    it('should parse a 24h HH:mm string, keeping hours and minutes', () => {
      const result = adapter.parseTime('21:30', 'HH:mm') as Date;

      expect(adapter.isValid(result)).toBeTruthy();
      expect(result.getHours()).toBe(21);
      expect(result.getMinutes()).toBe(30);
    });

    it('should parse an unpadded hour', () => {
      const result = adapter.parseTime('9:05', 'HH:mm') as Date;

      expect(adapter.isValid(result)).toBeTruthy();
      expect(result.getHours()).toBe(9);
      expect(result.getMinutes()).toBe(5);
    });

    it('should parse an optional seconds component', () => {
      const result = adapter.parseTime('23:59:07', 'HH:mm') as Date;

      expect(adapter.isValid(result)).toBeTruthy();
      expect(result.getHours()).toBe(23);
      expect(result.getMinutes()).toBe(59);
      expect(result.getSeconds()).toBe(7);
    });

    it('should reject AM/PM input that the native adapter would otherwise accept', () => {
      const result = adapter.parseTime('9:30 PM', 'HH:mm') as Date;
      expect(adapter.isValid(result)).toBeFalsy();
    });

    it('should reject out-of-range hours and minutes', () => {
      expect(adapter.isValid(adapter.parseTime('24:00', 'HH:mm') as Date)).toBeFalsy();
      expect(adapter.isValid(adapter.parseTime('12:60', 'HH:mm') as Date)).toBeFalsy();
    });

    it('should reject malformed time strings', () => {
      expect(adapter.isValid(adapter.parseTime('not-a-time', 'HH:mm') as Date)).toBeFalsy();
      expect(adapter.isValid(adapter.parseTime('1230', 'HH:mm') as Date)).toBeFalsy();
    });

    it('should fallback to NativeDateAdapter for empty and non-string values', () => {
      expect(adapter.parseTime('', 'HH:mm')).toBeNull();

      const existingDate = new Date(2026, 7, 6, 21, 30);
      expect(adapter.parseTime(existingDate, 'HH:mm')).toEqual(existingDate);
    });
  });

  describe('parse()', () => {
    it('should parse a valid yyyy-MM-dd string into a JS Date', () => {
      const result = adapter.parse('2026-08-06', 'yyyy-MM-dd');
      expect(result).toEqual(new Date(2026, 7, 6)); // Aug 6, 2026
    });

    it('should reject a rollover date (e.g., Feb 31) and return an invalid Date', () => {
      // 2026 is not a leap year, so Feb only has 28 days
      const result = adapter.parse('2026-02-31', 'yyyy-MM-dd') as Date;

      expect(result).toBeTruthy();
      expect(adapter.isValid(result)).toBeFalsy(); // Should trigger this.invalid()
    });

    it('should reject invalid string formats and fallback to native parsing', () => {
      const result = adapter.parse('not-a-date', 'yyyy-MM-dd') as Date;
      expect(adapter.isValid(result)).toBeFalsy();
    });

    it('should fallback to NativeDateAdapter if the value is already a Date object', () => {
      const existingDate = new Date(2026, 7, 6);
      const result = adapter.parse(existingDate, 'yyyy-MM-dd');
      expect(result).toEqual(existingDate);
    });
  });
});

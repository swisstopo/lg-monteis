import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/** Matches a strict 24h `HH:mm` (optionally `:ss`) time string. */
const ISO_TIME_REGEX = /^(\d{1,2}):([0-5]\d)(?::([0-5]\d))?$/;

/*
 * This adapter enforces the ISO date format (yyyy-MM-dd) for material date inputs
 * and the 24h ISO time format (HH:mm) for material time inputs.
 * It extends the NativeDateAdapter to provide custom formatting and parsing.
 */
@Injectable()
export class IsoDateAdapter extends NativeDateAdapter {
  override format(date: Date, displayFormat: any): string {
    if (!date || !this.isValid(date)) {
      return '';
    }

    // Only force ISO format when rendering the input field
    if (displayFormat === 'yyyy-MM-dd') {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    }

    // Force 24h time for the timepicker input and its dropdown option labels. The native
    // adapter delegates to `Intl.DateTimeFormat`, which renders AM/PM for 12h locales.
    if (displayFormat === 'HH:mm' || displayFormat === 'HH:mm:ss') {
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      if (displayFormat === 'HH:mm:ss') {
        return `${hours}:${minutes}:${String(date.getSeconds()).padStart(2, '0')}`;
      }
      return `${hours}:${minutes}`;
    }

    // Fallback to native adapter for header labels ('MMM yyyy', 'LL', etc.)
    return super.format(date, displayFormat);
  }

  /**
   * Parses strictly 24h `HH:mm[:ss]` input, rejecting AM/PM suffixes that the native adapter
   * would otherwise accept (e.g. '9:30 PM'), so typed input matches the displayed format.
   */
  override parseTime(value: unknown, parseFormat: string | string[]): Date | null {
    if (typeof value === 'string' && value.trim()) {
      const parsed = value.trim().match(ISO_TIME_REGEX);
      if (parsed) {
        const [, rawHours, rawMinutes, rawSeconds] = parsed;
        const hours = Number(rawHours);
        if (hours <= 23) {
          return this.setTime(this.today(), hours, Number(rawMinutes), Number(rawSeconds ?? 0));
        }
      }
      return this.invalid(); // Signals to Material that the input is invalid
    }
    return super.parseTime(value, parseFormat);
  }

  override parse(value: unknown, parseFormat: string | string[]): Date | null {
    if (typeof value === 'string' && value.trim()) {
      const parts = value.split('-').map(Number);
      if (parts.length === 3 && parts.every((part) => !Number.isNaN(part))) {
        const [year, month, day] = parts;
        const date = new Date(year, month - 1, day);

        // Strict check to prevent date rollover (e.g., Feb 31 becoming Mar 3)
        if (
          date.getFullYear() === year &&
          date.getMonth() === month - 1 &&
          date.getDate() === day
        ) {
          return date;
        }
        return this.invalid(); // Signals to Material that the input is invalid
      }
    }
    return super.parse(value, parseFormat);
  }
}

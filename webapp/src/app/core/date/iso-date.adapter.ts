import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';
/*
 * This adapter enforces the ISO date format (yyyy-MM-dd) for material date inputs.
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

    // Fallback to native adapter for header labels ('MMM yyyy', 'LL', etc.)
    return super.format(date, displayFormat);
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

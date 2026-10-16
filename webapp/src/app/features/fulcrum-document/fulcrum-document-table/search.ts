import { FulcrumDocumentResponseDto } from '@core/generated';

// every word has to match somewhere, so "opalinus 2019" narrows down instead of widening
export function matchesSearch(document: FulcrumDocumentResponseDto, term: string): boolean {
  const words = term.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return true;

  const text = [
    document.number,
    document.title,
    document.authors,
    document.year,
    document.category,
    ...(document.keywords ?? []),
  ]
    .filter((value) => value !== undefined && value !== null)
    .join('\n')
    .toLowerCase();
  return words.every((word) => text.includes(word));
}

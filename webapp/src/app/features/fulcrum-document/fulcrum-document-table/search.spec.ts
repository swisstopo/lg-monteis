import { fulcrumEntry } from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { describe, expect, it } from 'vitest';
import { matchesSearch } from './search';

const ENTRY = fulcrumEntry({
  number: 'TR 2008-04',
  title: 'Self-sealing of fractures in Opalinus Clay',
  authors: 'Bossart, P.; Thury, M.',
  year: 2019,
  category: 'Technical Report',
  keywords: ['hydrogeology', 'swelling'],
});

describe('matchesSearch', () => {
  it('matches everything without a search term', () => {
    expect(matchesSearch(ENTRY, '')).toBe(true);
    expect(matchesSearch(ENTRY, '   ')).toBe(true);
  });

  it.each([
    ['number', 'tr 2008'],
    ['title', 'OPALINUS'],
    ['authors', 'thury'],
    ['year', '2019'],
    ['category', 'technical'],
    ['keywords', 'swelling'],
  ])('matches on the %s, ignoring case', (_, term) => {
    expect(matchesSearch(ENTRY, term)).toBe(true);
  });

  it('needs every word to match, in any field', () => {
    expect(matchesSearch(ENTRY, 'bossart hydrogeology')).toBe(true);
    expect(matchesSearch(ENTRY, 'bossart granite')).toBe(false);
  });

  it('skips the fields an entry lacks', () => {
    const bare = fulcrumEntry({
      number: undefined,
      authors: undefined,
      year: undefined,
      category: undefined,
      keywords: [],
    });

    expect(matchesSearch(bare, 'fractures')).toBe(true);
    expect(matchesSearch(bare, 'undefined')).toBe(false);
  });
});

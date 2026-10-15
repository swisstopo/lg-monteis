import { fulcrumDocument } from '@features/fulcrum-document/testing/fulcrum-document-fixtures';
import { describe, expect, it } from 'vitest';
import { matchesSearch } from './search';

const DOCUMENT = fulcrumDocument({
  number: 'TR 2019-04',
  title: 'Self-sealing of fractures in Opalinus Clay',
  authors: 'Bossart, P.; Thury, M.',
  category: 'Technical Report',
  keywords: ['hydrogeology', 'swelling'],
});

describe('matchesSearch', () => {
  it('matches everything without a search term', () => {
    expect(matchesSearch(DOCUMENT, '')).toBe(true);
    expect(matchesSearch(DOCUMENT, '   ')).toBe(true);
  });

  it.each([
    ['number', 'tr 2019'],
    ['title', 'OPALINUS'],
    ['authors', 'thury'],
    ['category', 'technical'],
    ['keywords', 'swelling'],
  ])('matches on the %s, ignoring case', (_, term) => {
    expect(matchesSearch(DOCUMENT, term)).toBe(true);
  });

  it('needs every word to match, in any field', () => {
    expect(matchesSearch(DOCUMENT, 'bossart hydrogeology')).toBe(true);
    expect(matchesSearch(DOCUMENT, 'bossart granite')).toBe(false);
  });

  it('skips the fields a document lacks', () => {
    const bare = fulcrumDocument({
      number: undefined,
      authors: undefined,
      category: undefined,
      keywords: [],
    });

    expect(matchesSearch(bare, 'fractures')).toBe(true);
    expect(matchesSearch(bare, 'undefined')).toBe(false);
  });
});

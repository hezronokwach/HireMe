import {
  EMPTY_QUERY,
  filterChips,
  parseQuery,
  toParams,
  withoutFilter,
  withoutFilters,
} from './equipment-query';

describe('equipment query', () => {
  it('reads valid URL parameters', () => {
    expect(
      parseQuery({
        category: 'AGRICULTURAL',
        location: ' Nakuru ',
        minRate: '1000',
        maxRate: '8000',
        q: 'tractor',
        from: '2030-01-26',
        to: '2030-01-27',
        sort: 'dailyRateKes,asc',
        page: '2',
        size: '24',
      }),
    ).toEqual({
      category: 'AGRICULTURAL',
      location: 'Nakuru',
      minRate: 1000,
      maxRate: 8000,
      q: 'tractor',
      from: '2030-01-26',
      to: '2030-01-27',
      sort: 'dailyRateKes,asc',
      page: 2,
      size: 24,
    });
  });

  it('ignores malformed parameters instead of sending them to the API', () => {
    expect(
      parseQuery({
        category: 'BOATS',
        minRate: '-5',
        maxRate: 'lots',
        from: 'tomorrow',
        sort: 'password,asc',
        page: '-1',
        size: '1000',
        location: '   ',
      }),
    ).toEqual(EMPTY_QUERY);
  });

  it('writes short URLs: defaults are left out', () => {
    expect(toParams(EMPTY_QUERY)).toEqual({});
    expect(toParams({ ...EMPTY_QUERY, category: 'EVENT_SETUP', page: 1 })).toEqual({
      category: 'EVENT_SETUP',
      page: '1',
    });
  });

  it('removes one filter, or all of them, and goes back to page one', () => {
    const query = {
      ...EMPTY_QUERY,
      location: 'Nakuru',
      from: '2030-01-26',
      to: '2030-01-27',
      page: 2,
    };
    expect(withoutFilter(query, 'dates')).toEqual({ ...EMPTY_QUERY, location: 'Nakuru' });
    expect(withoutFilter(query, 'location')).toEqual({
      ...EMPTY_QUERY,
      from: '2030-01-26',
      to: '2030-01-27',
    });
    expect(withoutFilters({ ...query, size: 48 })).toEqual({ ...EMPTY_QUERY, size: 48 });
  });

  it('labels one chip per active filter', () => {
    const year = new Date().getFullYear();
    const chips = filterChips({
      ...EMPTY_QUERY,
      q: 'tractor',
      category: 'AGRICULTURAL',
      location: 'Nakuru',
      minRate: 1000,
      maxRate: 8000,
      from: `${year}-11-21`,
      to: `${year}-11-22`,
    });
    expect(chips.map((c) => c.key)).toEqual([
      'q',
      'category',
      'location',
      'minRate',
      'maxRate',
      'dates',
    ]);
    expect(chips.map((c) => c.label)).toEqual([
      '“tractor”',
      'Agricultural',
      'In Nakuru',
      expect.stringMatching(/^From KES\s?1,000\/day$/),
      expect.stringMatching(/^Up to KES\s?8,000\/day$/),
      'Free 21 Nov – 22 Nov',
    ]);
    expect(filterChips(EMPTY_QUERY)).toEqual([]);
  });
});

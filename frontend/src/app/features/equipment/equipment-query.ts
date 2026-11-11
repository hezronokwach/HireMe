import { Params } from '@angular/router';
import { EquipmentSearchCriteria } from '../../api';
import { formatRange } from '../../core/dates';
import { Category, categoryLabel, formatKes, isCategory } from './equipment-labels';

/**
 * What the equipment list shows. It lives in the URL's query parameters, so the back button,
 * reloads and shared links all keep the search.
 */
export interface EquipmentQuery {
  category: Category | null;
  location: string | null;
  minRate: number | null;
  maxRate: number | null;
  /** Keywords matched against name, location and description. */
  q: string | null;
  from: string | null;
  to: string | null;
  sort: SortOption;
  page: number;
  size: number;
}

export type FilterKey = 'category' | 'location' | 'minRate' | 'maxRate' | 'q' | 'dates';

export const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'dailyRateKes,asc', label: 'Price: low to high' },
  { value: 'dailyRateKes,desc', label: 'Price: high to low' },
  { value: 'name,asc', label: 'Name: A to Z' },
] as const;
export type SortOption = (typeof SORT_OPTIONS)[number]['value'];

export const PAGE_SIZES = [12, 24, 48] as const;
const DEFAULT_SORT: SortOption = 'createdAt,desc';
const DEFAULT_SIZE = 12;
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;

export const EMPTY_QUERY: EquipmentQuery = {
  category: null,
  location: null,
  minRate: null,
  maxRate: null,
  q: null,
  from: null,
  to: null,
  sort: DEFAULT_SORT,
  page: 0,
  size: DEFAULT_SIZE,
};

/** Reads the query from URL parameters, ignoring anything malformed. */
export function parseQuery(params: Params): EquipmentQuery {
  const text = (key: string) => {
    const value = typeof params[key] === 'string' ? (params[key] as string).trim() : '';
    return value || null;
  };
  const amount = (key: string) => {
    const value = Number(text(key) ?? NaN);
    return Number.isFinite(value) && value >= 0 ? value : null;
  };
  const day = (key: string) => {
    const value = text(key);
    return value && ISO_DAY.test(value) ? value : null;
  };
  const category = text('category');
  const sort = text('sort');
  const page = Number(text('page') ?? 0);
  const size = Number(text('size') ?? DEFAULT_SIZE);
  return {
    category: isCategory(category) ? category : null,
    location: text('location'),
    minRate: amount('minRate'),
    maxRate: amount('maxRate'),
    q: text('q'),
    from: day('from'),
    to: day('to'),
    sort: SORT_OPTIONS.some((o) => o.value === sort) ? (sort as SortOption) : DEFAULT_SORT,
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: (PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_SIZE,
  };
}

/** URL parameters for a query; defaults are left out so URLs stay short. */
export function toParams(query: EquipmentQuery): Params {
  const params: Params = {};
  const put = (key: string, value: string | number | null, skip?: string | number) => {
    if (value !== null && value !== '' && value !== skip) {
      params[key] = String(value);
    }
  };
  put('q', query.q);
  put('category', query.category);
  put('location', query.location);
  put('minRate', query.minRate);
  put('maxRate', query.maxRate);
  put('from', query.from);
  put('to', query.to);
  put('sort', query.sort, DEFAULT_SORT);
  put('page', query.page, 0);
  put('size', query.size, DEFAULT_SIZE);
  return params;
}

/** The filters an AI search worked out, as a query (keeping the user's sort and page size). */
export function fromCriteria(
  criteria: EquipmentSearchCriteria | undefined,
  current: EquipmentQuery,
): EquipmentQuery {
  return parseQuery({
    ...toParams({ ...EMPTY_QUERY, sort: current.sort, size: current.size }),
    ...(criteria?.category ? { category: criteria.category } : {}),
    ...(criteria?.location ? { location: criteria.location } : {}),
    ...(criteria?.minRate != null ? { minRate: String(criteria.minRate) } : {}),
    ...(criteria?.maxRate != null ? { maxRate: String(criteria.maxRate) } : {}),
    ...(criteria?.keywords ? { q: criteria.keywords } : {}),
    ...(criteria?.availableFrom ? { from: criteria.availableFrom } : {}),
    ...(criteria?.availableTo ? { to: criteria.availableTo } : {}),
  });
}

/** The query without one filter, back on the first page. */
export function withoutFilter(query: EquipmentQuery, key: FilterKey): EquipmentQuery {
  const next = { ...query, page: 0 };
  if (key === 'dates') {
    next.from = null;
    next.to = null;
  } else {
    next[key] = null;
  }
  return next;
}

export function withoutFilters(query: EquipmentQuery): EquipmentQuery {
  return { ...EMPTY_QUERY, sort: query.sort, size: query.size };
}

export interface FilterChip {
  key: FilterKey;
  label: string;
}

/** One chip per active filter, in the order the filter form shows them. */
export function filterChips(query: EquipmentQuery): FilterChip[] {
  const chips: FilterChip[] = [];
  if (query.q) {
    chips.push({ key: 'q', label: `“${query.q}”` });
  }
  if (query.category) {
    chips.push({ key: 'category', label: categoryLabel(query.category) });
  }
  if (query.location) {
    chips.push({ key: 'location', label: `In ${query.location}` });
  }
  if (query.minRate !== null) {
    chips.push({ key: 'minRate', label: `From ${formatKes(query.minRate)}/day` });
  }
  if (query.maxRate !== null) {
    chips.push({ key: 'maxRate', label: `Up to ${formatKes(query.maxRate)}/day` });
  }
  if (query.from || query.to) {
    const from = query.from ?? query.to!;
    chips.push({ key: 'dates', label: `Free ${formatRange(from, query.to ?? from)}` });
  }
  return chips;
}

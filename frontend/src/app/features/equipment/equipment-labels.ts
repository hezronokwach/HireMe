export type Category = 'HEAVY_MACHINERY' | 'AGRICULTURAL' | 'EVENT_SETUP';
export type EquipmentStatus = 'AVAILABLE' | 'UNDER_MAINTENANCE';

export const CATEGORIES: readonly { value: Category; label: string; icon: string }[] = [
  { value: 'HEAVY_MACHINERY', label: 'Heavy machinery', icon: 'construction' },
  { value: 'AGRICULTURAL', label: 'Agricultural', icon: 'agriculture' },
  { value: 'EVENT_SETUP', label: 'Event setup', icon: 'celebration' },
];

export function categoryLabel(value: string | null | undefined): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value ?? '';
}

export function categoryIcon(value: string | null | undefined): string {
  return CATEGORIES.find((c) => c.value === value)?.icon ?? 'inventory_2';
}

export function isCategory(value: string | null | undefined): value is Category {
  return CATEGORIES.some((c) => c.value === value);
}

export const STATUS_LABELS: Record<EquipmentStatus, string> = {
  AVAILABLE: 'Taking bookings',
  UNDER_MAINTENANCE: 'Under maintenance',
};

export function statusLabel(value: string | null | undefined): string {
  return STATUS_LABELS[value as EquipmentStatus] ?? value ?? '';
}

const KES = new Intl.NumberFormat('en-KE', {
  style: 'currency',
  currency: 'KES',
  currencyDisplay: 'code',
  maximumFractionDigits: 0,
});

/** "KES 6,000" (rounded to whole shillings). */
export function formatKes(amount: number | null | undefined): string {
  return amount == null ? '' : KES.format(amount);
}

export type Category = 'HEAVY_MACHINERY' | 'AGRICULTURAL' | 'EVENT_SETUP';
export type EquipmentStatus = 'AVAILABLE' | 'UNDER_MAINTENANCE';

export const CATEGORIES: readonly { value: Category; label: string; icon: string }[] = [
  { value: 'HEAVY_MACHINERY', label: 'Heavy machinery', icon: 'front_loader' },
  { value: 'AGRICULTURAL', label: 'Agricultural', icon: 'agriculture' },
  { value: 'EVENT_SETUP', label: 'Event setup', icon: 'festival' },
];

export function categoryLabel(value: string | null | undefined): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value ?? '';
}

export function categoryIcon(value: string | null | undefined): string {
  return CATEGORIES.find((c) => c.value === value)?.icon ?? 'inventory_2';
}

/**
 * The picture for a listing. Listings have no photos, so the name picks a Material Symbol for the kind of
 * equipment (a tractor, a tent, a PA system...), falling back to the category's.
 */
const NAME_ICONS: readonly [RegExp, string][] = [
  [/tractor|plough|plow|harrow|harvester|combine/i, 'agriculture'],
  [/excavator|backhoe|digger|loader|bulldozer|grader/i, 'front_loader'],
  [/forklift/i, 'forklift'],
  [/crane|hoist/i, 'precision_manufacturing'],
  [/tipper|truck|lorry|trailer/i, 'local_shipping'],
  [/roller|compactor|mixer|concrete|drill|breaker/i, 'construction'],
  [/pump|irrigat|borehole/i, 'water_pump'],
  [/spray/i, 'sanitizer'],
  [/generator|power/i, 'bolt'],
  [/tent|marquee|gazebo|canopy/i, 'festival'],
  [/chair|table|seat/i, 'chair'],
  [/sound|speaker|\bpa\b|microphone|audio|\bdj\b/i, 'speaker'],
  [/stage|platform|podium/i, 'theater_comedy'],
  [/light/i, 'light'],
];

export function equipmentIcon(
  name: string | null | undefined,
  category: string | null | undefined,
): string {
  const match = NAME_ICONS.find(([pattern]) => pattern.test(name ?? ''));
  return match ? match[1] : categoryIcon(category);
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

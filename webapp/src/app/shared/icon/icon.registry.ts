import Add16 from '@carbon/icons/es/add/16';
import ChevronRight16 from '@carbon/icons/es/chevron--right/16';
import Search16 from '@carbon/icons/es/search/16';
import Bm16 from '@carbon/icons/es/business-metrics/16';
import ChartLine16 from '@carbon/icons/es/chart--line/16';

export const ICONS: Record<string , unknown> = {
  'add': Add16,
  'chevron-right': ChevronRight16,
  'search': Search16,
  'business-metrics': Bm16,
  'chart': ChartLine16,
} as const;

export type IconName = keyof typeof ICONS;

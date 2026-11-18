// Hero banners (3:1, illustration on the right, empty left for the title).
// Upload and Muneem have no hero on purpose.
const BASE = 'https://res.cloudinary.com/voe6wsbo/image/upload';

const IDS = {
  dashboard: 'v1795007663/Modern_Rupee_Finance_Dashboard_Illustration.png',
  // TODO: same image as dashboard until the Expenses banner link arrives.
  expenses: 'v1795007663/Modern_Rupee_Finance_Dashboard_Illustration.png',
  rules: 'v1795007766/Automated_Rupee_Receipt_Routing.png',
  compare: 'v1795007796/Rupee_Dashboard_Comparison_Cards.png',
} as const;

export type HeroKey = keyof typeof IDS;

export interface HeroImage {
  src: string;
  srcSet: string;
}

/** Cloudinary-resized URLs (auto format/quality) so phones don't load the full PNG. */
export function heroImage(key: HeroKey): HeroImage {
  const url = (w: number) => `${BASE}/f_auto,q_auto,w_${w}/${IDS[key]}`;
  return { src: url(1600), srcSet: `${url(800)} 800w, ${url(1600)} 1600w, ${url(2400)} 2400w` };
}

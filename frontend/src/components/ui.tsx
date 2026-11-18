import { useState } from 'react';
import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react';
import { useCountUp } from '../lib/useCountUp';
import { heroImage, type HeroKey } from '../lib/heroImages';

export function Spinner({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent ${className}`}
    />
  );
}

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
const VARIANTS: Record<Variant, string> = {
  primary: 'bg-indigo-600 text-white hover:bg-indigo-700 disabled:bg-indigo-400',
  secondary: 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50 disabled:text-slate-400',
  danger: 'bg-white text-red-600 ring-1 ring-red-200 hover:bg-red-50 disabled:text-red-300',
  ghost: 'text-slate-600 hover:bg-slate-100 disabled:text-slate-300',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  busy?: boolean;
  size?: 'sm' | 'md';
}

/** Button with a spinner; disabled while `busy` so nothing is sent twice. */
export function Button({ variant = 'primary', busy = false, size = 'md', disabled, className = '', children, ...rest }: ButtonProps) {
  const pad = size === 'sm' ? 'px-2.5 py-1.5 text-xs' : 'px-4 py-2 text-sm';
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || busy}
      aria-busy={busy}
      className={`inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors disabled:cursor-not-allowed ${pad} ${VARIANTS[variant]} ${className}`}
    >
      {busy && <Spinner />}
      {children}
    </button>
  );
}

// Cards that mount close together (one page render) get increasing delays,
// so they fade in one after another; a later burst starts again from 0.
let burstStart = 0;
let burstIndex = 0;
function nextStaggerDelay(): number {
  const now = performance.now();
  if (now - burstStart > 300) {
    burstStart = now;
    burstIndex = 0;
  }
  return Math.min(burstIndex++, 8) * 60;
}

/** Keeps a stable stagger delay for the lifetime of the component. */
export function useStagger(): CSSProperties {
  const [delay] = useState(nextStaggerDelay);
  return { '--anim-delay': `${delay}ms` } as CSSProperties;
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  const style = useStagger();
  return (
    <section style={style} className={`anim-fade-up rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5 ${className}`}>
      {children}
    </section>
  );
}

/**
 * Recharts series animation, started after the card's fade-in (max stagger
 * 480ms + 450ms fade) so the bars visibly grow instead of finishing unseen.
 */
export const CHART_ANIM = { animationBegin: 550, animationDuration: 900, animationEasing: 'ease-out' } as const;

/** A number that counts up from 0 on mount (and to the new value when it changes). */
export function CountUp({ value, format = String }: { value: number; format?: (n: number) => string }) {
  const shown = useCountUp(value);
  return (
    <>
      <span aria-hidden="true">{format(shown)}</span>
      <span className="sr-only">{format(value)}</span>
    </>
  );
}

export function SkeletonBlock({ className = '' }: { className?: string }) {
  return <div aria-hidden="true" className={`animate-pulse rounded bg-slate-200 ${className}`} />;
}

export function SkeletonRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-3" aria-label="Loading" role="status">
      {Array.from({ length: rows }, (_, i) => (
        <SkeletonBlock key={i} className="h-10 w-full" />
      ))}
    </div>
  );
}

export function PageHeader({ title, hero, children }: { title: string; hero?: HeroKey; children?: ReactNode }) {
  if (hero) {
    const img = heroImage(hero);
    return (
      <div className="anim-fade-up mb-5">
        {/* Full-bleed: breaks out of <main>'s max-width and touches the header. */}
        <div className="relative left-1/2 -mt-6 -ml-[50vw] h-56 w-screen overflow-hidden border-b border-slate-200 bg-[#F5F6FB] sm:h-80 lg:h-[26rem]">
          {/* Height-driven and pinned right; its left edge fades into the same bg colour, so wide screens show no seam. */}
          <img
            src={img.src}
            srcSet={img.srcSet}
            sizes="(min-width: 1024px) 1250px, (min-width: 640px) 960px, 680px"
            alt=""
            width={2400}
            height={800}
            className="absolute right-0 top-0 h-full w-auto max-w-none [mask-image:linear-gradient(to_right,transparent,black_20%)]"
          />
          {/* Phones: the illustration slides under the title, so soften it there. */}
          <div className="absolute inset-0 bg-linear-to-r from-[#F5F6FB] via-[#F5F6FB]/70 to-transparent sm:hidden" aria-hidden="true" />
          <div className="relative mx-auto flex h-full max-w-6xl items-center px-4">
            <h1 className="text-2xl font-semibold text-slate-900 sm:text-4xl">{title}</h1>
          </div>
        </div>
        {children && <div className="mt-4 flex flex-wrap items-center justify-end gap-3">{children}</div>}
      </div>
    );
  }
  return (
    <div className="anim-fade-up mb-5 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
      {children}
    </div>
  );
}

/** 1 … 4 5 [6] 7 8 … 20 — always first, last and two either side of the current page. */
function pageList(page: number, total: number): (number | '…')[] {
  const out: (number | '…')[] = [];
  for (let p = 1; p <= total; p++) {
    if (p === 1 || p === total || Math.abs(p - page) <= 1 || (page <= 3 && p <= 4) || (page >= total - 2 && p >= total - 3)) {
      out.push(p);
    } else if (out[out.length - 1] !== '…') {
      out.push('…');
    }
  }
  return out;
}

/** "11–20 of 43" plus ← 1 2 3 … → buttons. `page` is 1-based. */
export function Pager({
  page,
  totalPages,
  first,
  last,
  total,
  noun = '',
  busy = false,
  onPage,
}: {
  page: number;
  totalPages: number;
  first: number;
  last: number;
  total: number;
  noun?: string;
  busy?: boolean;
  onPage: (page: number) => void;
}) {
  const btn = 'inline-flex h-8 min-w-8 items-center justify-center rounded-md px-2 text-sm font-medium tabular-nums transition-colors disabled:cursor-not-allowed';
  return (
    <nav aria-label="Pages" className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm">
      <p className="text-slate-500 tabular-nums">
        {first}–{last} of {total}
        {noun && ` ${noun}`}
      </p>
      {totalPages > 1 && (
        <div className="flex flex-wrap items-center gap-1">
          <button
            type="button"
            className={`${btn} text-slate-600 hover:bg-slate-100 disabled:text-slate-300 disabled:hover:bg-transparent`}
            disabled={busy || page <= 1}
            onClick={() => onPage(page - 1)}
            aria-label="Previous page"
          >
            ←
          </button>
          {pageList(page, totalPages).map((p, i) =>
            p === '…' ? (
              <span key={`gap-${i}`} className="px-1 text-slate-400" aria-hidden="true">
                …
              </span>
            ) : (
              <button
                key={p}
                type="button"
                disabled={busy}
                aria-current={p === page ? 'page' : undefined}
                aria-label={`Page ${p}`}
                onClick={() => p !== page && onPage(p)}
                className={`${btn} ${
                  p === page ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-700 ring-1 ring-slate-200 hover:bg-indigo-50 hover:text-indigo-700'
                }`}
              >
                {p}
              </button>
            ),
          )}
          <button
            type="button"
            className={`${btn} text-slate-600 hover:bg-slate-100 disabled:text-slate-300 disabled:hover:bg-transparent`}
            disabled={busy || page >= totalPages}
            onClick={() => onPage(page + 1)}
            aria-label="Next page"
          >
            →
          </button>
        </div>
      )}
    </nav>
  );
}

/** Inline error box for a section whose load failed (non-network errors). */
export function ErrorBox({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      <span>{message}</span>
      <Button variant="danger" size="sm" onClick={onRetry}>
        Retry
      </Button>
    </div>
  );
}

export function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null;
  return (
    <p id={id} className="mt-1 text-xs text-red-600">
      {message}
    </p>
  );
}

export const inputClass =
  'block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200 aria-[invalid=true]:border-red-400 aria-[invalid=true]:ring-red-100';

export const labelClass = 'mb-1 block text-sm font-medium text-slate-700';

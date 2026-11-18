import { useEffect, useRef, useState } from 'react';

const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/**
 * Animates a number from its previous value (0 on first mount) to `target`.
 * Intermediate frames are whole numbers; the last frame is exactly `target`.
 */
export function useCountUp(target: number, duration = 800): number {
  const [value, setValue] = useState(() => (reducedMotion() ? target : 0));
  const from = useRef(value);

  useEffect(() => {
    if (reducedMotion() || !Number.isFinite(target)) {
      from.current = target;
      setValue(target);
      return;
    }
    const start = performance.now();
    const begin = from.current;
    let raf = 0;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      const v = t >= 1 ? target : Math.round(begin + (target - begin) * eased);
      from.current = v;
      setValue(v);
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, duration]);

  return value;
}

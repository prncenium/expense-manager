import { useCallback, useEffect, useRef, useState } from 'react';
import { useServerStatus } from './serverStatus';
import { NetworkError, errorMessage } from '../api/client';

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  /** Error message for non-network errors (network errors show the global banner instead). */
  error: string | null;
  reload: () => void;
}

/**
 * Runs `fn` on mount, whenever `deps` change and when the user clicks Retry on the
 * "Can't reach server" banner. Keeps the previous data while reloading.
 */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const { retry } = useServerStatus();
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    fnRef
      .current()
      .then((d) => {
        if (alive) setData(d);
      })
      .catch((e: unknown) => {
        if (alive && !(e instanceof NetworkError)) setError(errorMessage(e));
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [...deps, retry, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, loading, error, reload };
}

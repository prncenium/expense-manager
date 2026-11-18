import { useSyncExternalStore } from 'react';

/**
 * Tiny global store: is the backend unreachable, plus a "retry" counter that pages
 * use as a reload trigger when the user clicks Retry on the banner.
 */
type State = { down: boolean; retry: number };

let state: State = { down: false, retry: 0 };
const listeners = new Set<() => void>();

function emit(next: State) {
  state = next;
  listeners.forEach((l) => l());
}

export const serverStatus = {
  setDown(down: boolean) {
    if (state.down !== down) emit({ ...state, down });
  },
  retry() {
    emit({ down: false, retry: state.retry + 1 });
  },
  subscribe(l: () => void) {
    listeners.add(l);
    return () => listeners.delete(l);
  },
  get: () => state,
};

export function useServerStatus(): State {
  return useSyncExternalStore(serverStatus.subscribe, serverStatus.get);
}

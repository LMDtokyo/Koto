import { createSignal, onCleanup } from "solid-js";
import type { StoreApi } from "zustand/vanilla";

/**
 * Bridge between zustand vanilla store and Solid's reactivity.
 * Returns an accessor (getter function) that updates when the selected slice changes.
 */
export function useSolidStore<T, S>(store: StoreApi<T>, selector: (state: T) => S): () => S {
  const [value, setValue] = createSignal(selector(store.getState()));
  const unsub = store.subscribe((state) => setValue(() => selector(state)));
  onCleanup(unsub);
  return value;
}

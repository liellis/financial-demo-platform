import { useSyncExternalStore } from "react";

export type Listener = () => void;

export interface Store<T extends object> {
  state: T;
  subscribe: (listener: Listener) => () => void;
  getVersion: () => number;
  notify: () => void;
}

/**
 * Minimal Vue-style reactive store for the merged demo portal.
 * Mutating `state` through the proxy notifies every React subscriber.
 */
export function createStore<T extends object>(initial: T): Store<T> {
  let version = 0;
  const listeners = new Set<Listener>();
  const notify = () => {
    version += 1;
    listeners.forEach((listener) => listener());
  };
  const state = new Proxy(initial, {
    set(target, key, value) {
      (target as Record<PropertyKey, unknown>)[key] = value;
      notify();
      return true;
    },
    deleteProperty(target, key) {
      delete (target as Record<PropertyKey, unknown>)[key];
      notify();
      return true;
    },
  });
  return {
    state,
    notify,
    getVersion: () => version,
    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

export function useStoreVersion(store: {
  subscribe: (listener: Listener) => () => void;
  getVersion: () => number;
}): number {
  return useSyncExternalStore(store.subscribe, store.getVersion, store.getVersion);
}

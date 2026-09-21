type AuthEvent = "signed-in" | "signed-out" | "refreshed";
type Message = { kind: "event"; event: AuthEvent };

const LOCK_NAME = "offertrack-auth";
const LOCK_WAIT_MS = 10_000;
let channel: BroadcastChannel | null = null;
let generation = 0;
let refreshVersion = 0;
let signedOut = false;
let queue: Promise<unknown> = Promise.resolve();
const listeners = new Set<(event: AuthEvent) => void>();

export class AuthCoordinationError extends Error {
  constructor() { super("Authentication could not be confirmed. Please sign in again."); }
}

const applyEvent = (event: AuthEvent) => {
  if (event === "refreshed") refreshVersion += 1;
  else {
    generation += 1;
    signedOut = event === "signed-out";
  }
  for (const listener of listeners) listener(event);
};

const getChannel = (): BroadcastChannel | null => {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") return null;
  if (!channel) {
    channel = new BroadcastChannel(LOCK_NAME);
    channel.onmessage = ({ data }: MessageEvent<unknown>) => {
      if (!data || typeof data !== "object") return;
      const value = data as Partial<Message>;
      if (value.kind === "event" && (value.event === "signed-in" ||
          value.event === "signed-out" || value.event === "refreshed")) {
        applyEvent(value.event);
      }
    };
  }
  return channel;
};

export const authState = () => ({ generation, refreshVersion, signedOut });
export const emitAuthEvent = (event: AuthEvent): void => {
  applyEvent(event);
  getChannel()?.postMessage({ kind: "event", event });
};
export const subscribeAuthEvents = (listener: (event: AuthEvent) => void): (() => void) => {
  getChannel();
  listeners.add(listener);
  return () => { listeners.delete(listener); };
};

const acquire = async <T,>(work: () => Promise<T>, requireCoordination: boolean): Promise<T> => {
  getChannel();
  if (typeof navigator !== "undefined" && navigator.locks?.request) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), LOCK_WAIT_MS);
    try {
      return await navigator.locks.request(LOCK_NAME, { mode: "exclusive", signal: controller.signal }, async () => {
        clearTimeout(timeout);
        return work();
      });
    } catch (error) {
      if (controller.signal.aborted) throw new AuthCoordinationError();
      throw error;
    } finally { clearTimeout(timeout); }
  }
  // Explicit sign-in/out can still run locally. Refresh must never rotate without
  // a cross-tab lock: BroadcastChannel events cannot guarantee mutual exclusion.
  if (requireCoordination) throw new AuthCoordinationError();
  return work();
};

/** Serializes lifecycle work in this tab and uses Web Locks across tabs when available. */
export const withAuthLock = <T,>(work: () => Promise<T>, requireCoordination = false): Promise<T> => {
  const result = queue.then(() => acquire(work, requireCoordination));
  queue = result.then(() => undefined, () => undefined);
  return result;
};

type AuthEvent = "signed-in" | "signed-out" | "refreshed";
type Message =
  | { kind: "event"; event: AuthEvent }
  | { kind: "claim" | "held" | "released"; ticket: string };

const LOCK_NAME = "offertrack-auth";
const WAIT_MS = 10_000;
const ELECTION_MS = 200;
let channel: BroadcastChannel | null = null;
let generation = 0;
let refreshVersion = 0;
let signedOut = false;
let queue: Promise<unknown> = Promise.resolve();
let activeTicket: string | null = null;
const candidates = new Map<string, number>();
const listeners = new Set<(event: AuthEvent) => void>();
const messages = new Set<(message: Message) => void>();

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
      const value = data as Partial<Message> & { event?: unknown; ticket?: unknown };
      if (value.kind === "event" && ["signed-in", "signed-out", "refreshed"].includes(String(value.event))) {
        applyEvent(value.event as AuthEvent);
      } else if (["claim", "held", "released"].includes(String(value.kind))
          && typeof value.ticket === "string" && /^[a-f0-9-]{36}$/.test(value.ticket)) {
        const message = value as Message;
        if (value.kind === "claim") {
          candidates.set(value.ticket, Date.now());
          if (activeTicket) channel?.postMessage({ kind: "held", ticket: activeTicket });
        }
        if (value.kind === "released") candidates.delete(value.ticket);
        for (const listener of messages) listener(message);
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

const fallbackLock = async <T,>(work: () => Promise<T>, requireCoordination: boolean,
    deadline = Date.now() + WAIT_MS): Promise<T> => {
  if (Date.now() + ELECTION_MS >= deadline) throw new AuthCoordinationError();
  const bus = getChannel();
  if (!bus) {
    if (requireCoordination && typeof window !== "undefined") throw new AuthCoordinationError();
    return work();
  }
  const ticket = crypto.randomUUID();
  const started = Date.now();
  let holder: string | null = null;
  let released = false;
  let releaseWait: (() => void) | null = null;
  const onMessage = (message: Message) => {
    if (message.kind === "held" && message.ticket !== ticket) holder = message.ticket;
    if (message.kind === "released" && message.ticket === holder) {
      released = true;
      releaseWait?.();
    }
  };
  messages.add(onMessage);
  // Stale claims are discarded only before election, never used to take over an ambiguous owner.
  for (const [candidate, created] of candidates) if (created < started - WAIT_MS) candidates.delete(candidate);
  candidates.set(ticket, started);
  bus.postMessage({ kind: "claim", ticket });
  try {
    await new Promise(resolve => setTimeout(resolve, ELECTION_MS));
    const leader = holder ?? [...candidates.keys()].sort()[0];
    if (leader !== ticket) {
      holder = leader;
      if (!released) {
        await new Promise<void>((resolve, reject) => {
          const timer = setTimeout(() => reject(new AuthCoordinationError()), Math.max(1, deadline - Date.now()));
          releaseWait = () => { clearTimeout(timer); resolve(); };
        });
      }
      // Re-enter a fresh election and recheck /api/me under the acquired lock.
      candidates.delete(ticket);
      bus.postMessage({ kind: "released", ticket });
      messages.delete(onMessage);
      return fallbackLock(work, requireCoordination, deadline);
    }
    activeTicket = ticket;
    bus.postMessage({ kind: "held", ticket });
    return await work();
  } finally {
    if (activeTicket === ticket) {
      activeTicket = null;
      bus.postMessage({ kind: "released", ticket });
    }
    candidates.delete(ticket);
    messages.delete(onMessage);
  }
};

const acquire = async <T,>(work: () => Promise<T>, requireCoordination: boolean): Promise<T> => {
  getChannel();
  if (typeof navigator !== "undefined" && navigator.locks?.request) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), WAIT_MS);
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
  return fallbackLock(work, requireCoordination);
};

/** Serializes refresh and explicit login/logout within and across cooperating tabs. */
export const withAuthLock = <T,>(work: () => Promise<T>, requireCoordination = false): Promise<T> => {
  const result = queue.then(() => acquire(work, requireCoordination));
  queue = result.then(() => undefined, () => undefined);
  return result;
};

type Coordinator = typeof import("./auth-coordinator");

class Bus {
  static peers: Bus[] = [];
  static sent: unknown[] = [];
  onmessage: ((event: { data: unknown }) => void) | null = null;
  constructor() { Bus.peers.push(this); }
  postMessage(data: unknown) {
    Bus.sent.push(data);
    for (const peer of Bus.peers) if (peer !== this) queueMicrotask(() => peer.onmessage?.({ data }));
  }
}
const tab = (): Coordinator => {
  let coordinator!: Coordinator;
  jest.isolateModules(() => { coordinator = jest.requireActual("./auth-coordinator"); });
  coordinator.subscribeAuthEvents(() => undefined);
  return coordinator;
};

beforeEach(() => {
  jest.useFakeTimers();
  Bus.peers = []; Bus.sent = [];
  Object.defineProperty(globalThis, "window", { configurable: true, value: {} });
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {} });
  Object.defineProperty(globalThis, "BroadcastChannel", { configurable: true, value: Bus });
});
afterEach(() => { jest.useRealTimers(); });

it.each([true, false])("uses Web Locks across tabs with BroadcastChannel available=%s", async available => {
  if (!available) Object.defineProperty(globalThis, "BroadcastChannel", { configurable: true, value: undefined });
  let queue = Promise.resolve();
  const request = jest.fn((_name, _options, callback: () => Promise<void>) => {
    const result = queue.then(callback);
    queue = result.catch(() => undefined);
    return result;
  });
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { locks: { request } } });
  const a = tab(), b = tab();
  const order: string[] = [];
  let release!: () => void;
  const first = a.withAuthLock(async () => {
    order.push("first-start");
    await new Promise<void>(resolve => { release = resolve; });
    order.push("first-end");
  }, true);
  const second = b.withAuthLock(async () => { order.push("second"); }, true);
  await jest.advanceTimersByTimeAsync(1);
  expect(order).toEqual(["first-start"]);
  release();
  await Promise.all([first, second]);
  expect(order).toEqual(["first-start", "first-end", "second"]);
  expect(request).toHaveBeenCalledTimes(2);
  expect(Bus.sent).toEqual([]);
  expect(jest.getTimerCount()).toBe(0);
});

it("serializes explicit lifecycle work in one tab without Web Locks, including after failure", async () => {
  const coordinator = tab();
  const order: string[] = [];
  let release!: () => void;
  const first = coordinator.withAuthLock(async () => {
    order.push("first-start");
    await new Promise<void>(resolve => { release = resolve; });
    throw new Error("Operation failed");
  });
  const rejected = expect(first).rejects.toThrow("Operation failed");
  const second = coordinator.withAuthLock(async () => { order.push("second"); return "signed-in"; });
  await jest.advanceTimersByTimeAsync(1);
  expect(order).toEqual(["first-start"]);
  release();
  await rejected;
  await expect(second).resolves.toBe("signed-in");
  expect(order).toEqual(["first-start", "second"]);
  expect(Bus.sent).toEqual([]);
});

it.each([true, false])("rejects required coordination without Web Locks with BroadcastChannel available=%s", async available => {
  if (!available) Object.defineProperty(globalThis, "BroadcastChannel", { configurable: true, value: undefined });
  const coordinator = tab();
  const work = jest.fn(async () => undefined);
  await expect(coordinator.withAuthLock(work, true)).rejects.toBeInstanceOf(coordinator.AuthCoordinationError);
  expect(work).not.toHaveBeenCalled();
  expect(Bus.sent).toEqual([]);
  expect(jest.getTimerCount()).toBe(0);
  // The rejected refresh must not block a subsequent explicit sign-in.
  await expect(coordinator.withAuthLock(async () => "signed-in")).resolves.toBe("signed-in");
});

it("bounds Web Lock waiting without running the work or falling back", async () => {
  const request = jest.fn((_name: string, { signal }: { signal: AbortSignal }) => new Promise((_, reject) => {
    signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
  }));
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { locks: { request } } });
  const coordinator = tab();
  const work = jest.fn(async () => undefined);
  const rejected = expect(coordinator.withAuthLock(work, true)).rejects.toBeInstanceOf(coordinator.AuthCoordinationError);
  await jest.advanceTimersByTimeAsync(10_000);
  await rejected;
  expect(work).not.toHaveBeenCalled();
  expect(request).toHaveBeenCalledTimes(1);
  expect(Bus.sent).toEqual([]);
  expect(jest.getTimerCount()).toBe(0);
});

it("does not run unlocked work when Web Locks rejects the request", async () => {
  const failure = new Error("Lock unavailable");
  const request = jest.fn().mockRejectedValue(failure);
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { locks: { request } } });
  const coordinator = tab();
  const work = jest.fn(async () => undefined);
  await expect(coordinator.withAuthLock(work, true)).rejects.toBe(failure);
  expect(work).not.toHaveBeenCalled();
  expect(request).toHaveBeenCalledTimes(1);
  expect(Bus.sent).toEqual([]);
  expect(jest.getTimerCount()).toBe(0);
});

it("propagates auth state events without credentials or account identifiers", async () => {
  const a = tab(), b = tab();
  const listener = jest.fn();
  b.subscribeAuthEvents(listener);
  a.emitAuthEvent("signed-out");
  await jest.advanceTimersByTimeAsync(1);
  expect(b.authState()).toEqual({ generation: 1, signedOut: true, refreshVersion: 0 });
  a.emitAuthEvent("signed-in");
  await jest.advanceTimersByTimeAsync(1);
  expect(b.authState()).toEqual({ generation: 2, signedOut: false, refreshVersion: 0 });
  a.emitAuthEvent("refreshed");
  await jest.advanceTimersByTimeAsync(1);
  expect(b.authState()).toEqual({ generation: 2, signedOut: false, refreshVersion: 1 });
  expect(listener.mock.calls).toEqual([["signed-out"], ["signed-in"], ["refreshed"]]);
  expect(Bus.sent).toEqual([
    { kind: "event", event: "signed-out" },
    { kind: "event", event: "signed-in" },
    { kind: "event", event: "refreshed" },
  ]);
});

it("ignores messages other than recognized auth state events", async () => {
  tab();
  const receiver = tab();
  const listener = jest.fn();
  receiver.subscribeAuthEvents(listener);
  for (const data of [null, {}, { kind: "unknown", event: "signed-in" }, { kind: "event", event: "unknown" }]) {
    Bus.peers[0].postMessage(data);
  }
  await jest.advanceTimersByTimeAsync(1);
  expect(listener).not.toHaveBeenCalled();
  expect(receiver.authState()).toEqual({ generation: 0, signedOut: false, refreshVersion: 0 });
});

import { randomUUID } from "node:crypto";
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
  Object.defineProperty(globalThis, "crypto", { configurable: true, value: { randomUUID } });
  Object.defineProperty(globalThis, "BroadcastChannel", { configurable: true, value: Bus });
});
afterEach(() => { jest.useRealTimers(); });

it("uses Web Locks to serialize lifecycle operations across tabs", async () => {
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
});

it("elects one fallback owner and lets a waiting tab recheck after release", async () => {
  const a = tab(), b = tab();
  let active = 0, maximum = 0, completed = 0;
  const work = async () => {
    active++; maximum = Math.max(maximum, active);
    await new Promise(resolve => setTimeout(resolve, 100));
    active--; completed++;
  };
  const pending = Promise.all([a.withAuthLock(work, true), b.withAuthLock(work, true)]);
  await jest.advanceTimersByTimeAsync(1000);
  await pending;
  expect(maximum).toBe(1);
  expect(completed).toBe(2);
});

it("bounds fallback waiting and never takes over an ambiguous owner", async () => {
  const a = tab(), b = tab();
  let release!: () => void;
  const first = a.withAuthLock(() => new Promise<void>(resolve => { release = resolve; }), true);
  await jest.advanceTimersByTimeAsync(250);
  const work = jest.fn(async () => undefined);
  const second = b.withAuthLock(work, true);
  const rejected = expect(second).rejects.toThrow("Authentication could not be confirmed");
  await jest.advanceTimersByTimeAsync(10_100);
  await rejected;
  expect(work).not.toHaveBeenCalled();
  release(); await first;
});

it("propagates logout without sending credentials or account identifiers", async () => {
  const a = tab(), b = tab();
  a.emitAuthEvent("signed-out");
  await jest.advanceTimersByTimeAsync(1);
  expect(b.authState().signedOut).toBe(true);
  expect(Bus.sent).toEqual([{ kind: "event", event: "signed-out" }]);
});


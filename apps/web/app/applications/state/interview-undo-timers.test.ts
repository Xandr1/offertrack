import { createInterviewUndoTimers } from "./interview-undo-timers";

describe("interview-undo-timers", () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("expires undo rows after 3 seconds", () => {
    const onExpire = jest.fn();
    const timers = createInterviewUndoTimers(onExpire);

    timers.schedule("undo-1");
    jest.advanceTimersByTime(2999);
    expect(onExpire).not.toHaveBeenCalled();

    jest.advanceTimersByTime(1);
    expect(onExpire).toHaveBeenCalledWith("undo-1");
  });

  it("cancels specific undo timers", () => {
    const onExpire = jest.fn();
    const timers = createInterviewUndoTimers(onExpire);

    timers.schedule("undo-1");
    timers.clear("undo-1");
    jest.advanceTimersByTime(3000);

    expect(onExpire).not.toHaveBeenCalled();
  });

  it("clears all timers", () => {
    const onExpire = jest.fn();
    const timers = createInterviewUndoTimers(onExpire);

    timers.schedule("undo-1");
    timers.schedule("undo-2");
    timers.clearAll();
    jest.advanceTimersByTime(3000);

    expect(onExpire).not.toHaveBeenCalled();
  });
});

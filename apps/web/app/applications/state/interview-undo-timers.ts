const UNDO_TIMEOUT_MS = 3000;

export type InterviewUndoTimers = {
  clear: (undoId: string) => void;
  clearAll: () => void;
  schedule: (undoId: string) => void;
};

export const createInterviewUndoTimers = (
  onExpire: (undoId: string) => void,
  timeoutMs = UNDO_TIMEOUT_MS,
): InterviewUndoTimers => {
  const timers = new Map<string, ReturnType<typeof setTimeout>>();

  const clear = (undoId: string) => {
    const timeoutId = timers.get(undoId);
    if (!timeoutId) {
      return;
    }

    clearTimeout(timeoutId);
    timers.delete(undoId);
  };

  const clearAll = () => {
    for (const timeoutId of timers.values()) {
      clearTimeout(timeoutId);
    }

    timers.clear();
  };

  const schedule = (undoId: string) => {
    clear(undoId);

    const timeoutId = setTimeout(() => {
      timers.delete(undoId);
      onExpire(undoId);
    }, timeoutMs);

    timers.set(undoId, timeoutId);
  };

  return {
    clear,
    clearAll,
    schedule,
  };
};

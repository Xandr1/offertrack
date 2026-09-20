"use client";

import { useEffect, useRef, useState } from "react";

/** Fragments never reach HTTP request URLs; retain the one-shot credential only in page memory. */
export const useRecoveryToken = () => {
  const parsed = useRef<{ token: string } | null>(null);
  const [state, setState] = useState({ ready: false, token: "" });
  useEffect(() => {
    if (!parsed.current) {
      const values = new URLSearchParams(window.location.hash.slice(1)).getAll("token");
      const token = values.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(values[0]) ? values[0] : "";
      parsed.current = { token };
      window.history.replaceState(window.history.state, "", window.location.pathname + window.location.search);
    }
    const token = parsed.current.token;
    let mounted = true;
    queueMicrotask(() => { if (mounted) setState({ ready: true, token }); });
    return () => { mounted = false; };
  }, []);
  return state;
};

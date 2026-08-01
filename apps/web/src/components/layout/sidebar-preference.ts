const SIDEBAR_PREFERENCE_EVENT = "offertrack:sidebar-preference";

export const SIDEBAR_DOCUMENT_ATTRIBUTE = "data-offertrack-sidebar";

export const SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT =
  'try{document.documentElement.setAttribute("data-offertrack-sidebar",window.localStorage.getItem("offertrack.sidebar.expanded")==="false"?"collapsed":"expanded")}catch{document.documentElement.setAttribute("data-offertrack-sidebar","expanded")}';

let inMemoryPreference: boolean | null = null;

export const parseStoredSidebarExpanded = (value: string | null): boolean =>
  value === "false" ? false : true;

export const readSidebarExpandedPreference = (): boolean => {
  if (typeof window === "undefined") {
    return true;
  }

  try {
    const expanded = parseStoredSidebarExpanded(
      window.localStorage.getItem("offertrack.sidebar.expanded"),
    );
    inMemoryPreference = expanded;
    return expanded;
  } catch {
    return inMemoryPreference ?? true;
  }
};

export const applySidebarExpandedPreference = (expanded: boolean): void => {
  if (typeof document === "undefined") {
    return;
  }

  document.documentElement.setAttribute(
    SIDEBAR_DOCUMENT_ATTRIBUTE,
    expanded ? "expanded" : "collapsed",
  );
};

export const storeSidebarExpandedPreference = (expanded: boolean): void => {
  inMemoryPreference = expanded;

  try {
    window.localStorage.setItem(
      "offertrack.sidebar.expanded",
      String(expanded),
    );
  } catch {
    // The in-memory preference remains usable when storage is unavailable.
  }

  applySidebarExpandedPreference(expanded);
  window.dispatchEvent(new Event(SIDEBAR_PREFERENCE_EVENT));
};

export const subscribeSidebarExpandedPreference = (
  onStoreChange: () => void,
): (() => void) => {
  if (typeof window === "undefined") {
    return () => undefined;
  }

  const syncPreference = () => {
    applySidebarExpandedPreference(readSidebarExpandedPreference());
    onStoreChange();
  };
  const handleStorage = (event: StorageEvent) => {
    if (
      event.key !== null &&
      event.key !== "offertrack.sidebar.expanded"
    ) {
      return;
    }

    syncPreference();
  };

  window.addEventListener(SIDEBAR_PREFERENCE_EVENT, syncPreference);
  window.addEventListener("storage", handleStorage);

  return () => {
    window.removeEventListener(SIDEBAR_PREFERENCE_EVENT, syncPreference);
    window.removeEventListener("storage", handleStorage);
  };
};

export const getExpandedSidebarServerSnapshot = (): boolean => true;

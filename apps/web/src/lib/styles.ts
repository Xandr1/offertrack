export const pageStyles = {
  centered:
    "flex min-h-screen items-center justify-center bg-zinc-100 px-4",
  statusMessage: "text-sm font-medium text-zinc-800",
  authForm: "mt-6 space-y-4",
  authFooter: "mt-4 text-sm text-zinc-700",
  appMain:
    "min-h-screen bg-[radial-gradient(circle_at_top_left,#eef2ff,transparent_32rem),linear-gradient(to_bottom,#fafafa,#f4f4f5)] px-4 py-6 text-zinc-950 md:px-8 md:py-8",
};

export const shellStyles = {
  grid: "mx-auto grid max-w-7xl grid-cols-1 md:grid-cols-[190px_1fr]",
  sidebar:
    "rounded-t-3xl border border-zinc-200 bg-white p-4 shadow-sm md:rounded-l-3xl md:rounded-r-none md:border-r-0",
  brand: "mb-8 flex items-center gap-2 text-sm font-bold text-zinc-950",
  logo: "h-8 w-8",
  nav: "space-y-1.5 text-sm",
  navLink:
    "block rounded-xl px-3 py-2 text-zinc-600 transition hover:bg-zinc-50 hover:text-zinc-900",
  navLinkActive:
    "block rounded-xl bg-violet-50 px-3 py-2 font-medium text-violet-700",
  panel:
    "rounded-b-3xl border border-t-0 border-zinc-200 bg-white/90 p-5 shadow-sm backdrop-blur md:rounded-l-none md:rounded-r-3xl md:border-l md:border-t md:p-8",
};

export const layoutStyles = {
  container: "mx-auto max-w-5xl",
  header:
    "flex items-center justify-between rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm",
  splitHeader: "flex flex-col gap-4 md:flex-row md:items-end md:justify-between",
  section: "mt-6",
  metricsGrid: "mt-6 grid gap-4 md:grid-cols-5",
  actionRow: "flex items-center gap-3",
  stackMd: "space-y-4",
};

export const homeStyles = {
  root: "min-h-screen bg-zinc-50 text-zinc-950",
  page:
    "mx-auto flex min-h-screen w-full max-w-6xl flex-col px-4 py-5 sm:px-6 lg:px-8",
  header:
    "flex items-center justify-between border-b border-zinc-200 pb-5",
  brand: "flex items-center gap-3 text-sm font-bold text-zinc-950",
  logo: "h-10 w-10",
  hero:
    "mx-auto flex w-full max-w-3xl flex-col items-center py-12 text-center md:py-16",
  eyebrow:
    "rounded-full border border-violet-200 bg-violet-50 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-violet-700",
  title:
    "mt-5 text-4xl font-bold leading-tight text-zinc-950 sm:text-5xl",
  description:
    "mt-5 max-w-2xl text-base leading-7 text-zinc-700 sm:text-lg",
  actions: "mt-8 flex w-full flex-col gap-3 sm:w-auto sm:flex-row",
  ctaLink: "w-full px-5 sm:w-auto",
  features: "grid gap-4 pb-10 sm:grid-cols-2 lg:grid-cols-4",
  featureCard: "h-full",
  featureTitle: "text-base font-semibold text-zinc-950",
  featureDescription: "mt-3 text-sm leading-6 text-zinc-700",
  footer:
    "border-t border-zinc-200 py-5 text-center text-xs font-medium text-zinc-500",
};

export const cardStyles = {
  auth: "w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm",
  default: "rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm",
  soft:
    "rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-[0_10px_26px_-20px_rgba(24,24,27,0.55)]",
  dashed:
    "rounded-2xl border border-dashed border-zinc-300 bg-white p-8 text-center",
  application:
    "rounded-2xl border border-zinc-200/80 bg-white p-3.5 shadow-[0_10px_24px_-22px_rgba(24,24,27,0.55)] transition hover:border-violet-200 hover:shadow-md",
};

export const textStyles = {
  pageTitle: "text-2xl font-semibold tracking-tight text-zinc-950",
  pageHeadline: "text-3xl font-bold tracking-tight text-zinc-950",
  sectionTitle: "text-lg font-semibold text-zinc-950",
  description: "mt-1 text-sm text-zinc-700",
  subtitle: "text-sm text-zinc-600",
  label: "text-sm font-medium text-zinc-950",
  helper: "mt-1 text-xs text-zinc-700",
  helperError: "mt-1.5 text-xs font-medium text-red-600",
  muted: "text-sm text-zinc-700",
  strong: "font-medium text-zinc-950",
  tinyMuted: "text-xs text-zinc-500",
  timestamp: "mt-2 text-xs text-zinc-700",
  statValue: "mt-2 text-3xl font-semibold text-zinc-950",
};

export const formStyles = {
  input:
    "mt-1 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-950 outline-none focus:border-zinc-900",
  inputSoft:
    "h-10 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-950 outline-none transition placeholder:text-zinc-400 focus:border-violet-600 focus:ring-2 focus:ring-violet-100 disabled:bg-zinc-50 disabled:text-zinc-500",
  inputSoftWithIcon:
    "h-10 w-full rounded-lg border border-zinc-300 bg-white py-2 pl-9 pr-3 text-sm text-zinc-950 outline-none transition placeholder:text-zinc-400 focus:border-violet-600 focus:ring-2 focus:ring-violet-100 disabled:bg-zinc-50 disabled:text-zinc-500",
  textareaSoft:
    "w-full rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-950 outline-none transition placeholder:text-zinc-400 focus:border-violet-600 focus:ring-2 focus:ring-violet-100 disabled:bg-zinc-50 disabled:text-zinc-500",
  selectSoft:
    "h-10 w-full rounded-lg border border-zinc-300 bg-white px-3 text-sm text-zinc-950 outline-none transition focus:border-violet-600 focus:ring-2 focus:ring-violet-100 disabled:bg-zinc-50 disabled:text-zinc-500",
  interviewStatusSelect:
    "w-40 rounded-lg border border-zinc-300 bg-white pb-[5px] pl-3 pr-[5px] pt-[5px] text-sm font-medium text-zinc-900 shadow-sm outline-none focus:border-violet-600 focus:ring-2 focus:ring-violet-100",
  primaryButton:
    "w-full rounded-xl bg-zinc-950 px-4 py-2 font-medium text-white disabled:opacity-60",
  inlinePrimarySoftButton:
    "inline-flex h-10 items-center justify-center rounded-lg bg-violet-700 px-4 text-sm font-medium text-white shadow-sm shadow-violet-900/10 transition hover:bg-violet-800 disabled:cursor-not-allowed disabled:opacity-60",
  error:
    "rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700",
};

export const buttonStyles = {
  secondary:
    "rounded-xl border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-950 hover:bg-zinc-50",
  danger:
    "rounded-xl border border-red-200 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-50",
  secondarySoft:
    "inline-flex h-10 items-center justify-center rounded-lg border border-zinc-300 bg-white px-4 text-sm font-medium text-zinc-900 transition hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-60",
  secondarySoftAccent:
    "inline-flex h-9 items-center justify-center rounded-lg border border-violet-300 bg-white px-3.5 text-sm font-medium text-violet-700 transition hover:bg-violet-50 disabled:cursor-not-allowed disabled:opacity-60",
  ghost:
    "inline-flex items-center justify-center rounded-lg px-2.5 py-1.5 text-sm font-medium text-zinc-700 transition hover:bg-zinc-100 hover:text-zinc-950 disabled:cursor-not-allowed disabled:opacity-60",
  ghostDanger:
    "inline-flex items-center justify-center rounded-lg px-2.5 py-1.5 text-sm font-medium text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60",
  iconGhostDanger:
    "inline-flex h-8 w-8 items-center justify-center rounded-lg text-red-500 transition hover:bg-red-50 disabled:opacity-60",
  iconGhost:
    "inline-flex h-9 w-9 items-center justify-center rounded-lg text-zinc-500 transition hover:bg-zinc-100 hover:text-zinc-950 disabled:cursor-not-allowed disabled:opacity-60",
  textAccent:
    "text-xs font-semibold text-violet-700 transition hover:text-violet-900 disabled:opacity-60",
  pill:
    "rounded-full bg-zinc-100 px-3 py-1 text-xs font-medium text-zinc-800",
  pillAccent:
    "rounded-full bg-violet-100 px-3 py-1 text-md font-medium text-zinc-800",
  link: "text-sm font-medium text-zinc-950 underline",
};

export const modalStyles = {
  softContainer:
    "fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/40 p-4 backdrop-blur-sm",
  softPanel:
    "w-full max-w-[900px] overflow-hidden rounded-3xl border border-zinc-200 bg-white shadow-2xl",
  compactPanel:
    "w-full max-w-md overflow-hidden rounded-3xl border border-zinc-200 bg-white shadow-2xl",
  softHeader:
    "flex items-start justify-between gap-4 border-b border-zinc-200 px-6 py-4",
  softBody: "max-h-[82vh] overflow-y-auto px-6 pb-0 pt-4",
  compactBody: "px-6 py-4",
  softFooterBleedCompact:
    "-mx-6 -mb-4 flex items-center justify-end gap-3 border-t border-zinc-200 bg-zinc-50 px-6 py-4",
  softFooterBleed:
    "sticky bottom-0 -mx-6 mt-3 flex items-center justify-end gap-3 border-t border-zinc-200 bg-zinc-50 px-6 py-4",
};

export const sectionStyles = {
  toolbar:
    "grid gap-3 rounded-2xl border border-zinc-200 bg-zinc-50/70 p-3 md:grid-cols-[170px_1fr_210px]",
  searchIcon:
    "pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400",
  softPanel: "rounded-xl border border-zinc-200 bg-zinc-50 p-3",
  accentPanel:
    "space-y-3 rounded-2xl border-2 border-violet-100/80 p-3.5",
  cardTopRow: "flex items-start justify-between gap-3",
  interviewHighlight:
    "mt-3 flex h-10 items-center justify-between gap-2 rounded-xl border pb-1 pl-2.5 pr-[5px] pt-1 text-sm",
  dashedEmpty:
    "rounded-xl border border-dashed border-zinc-300 px-4 py-6 text-center",
  listItem: "rounded-xl border border-zinc-200 px-4 py-3",
  splitRow: "flex flex-wrap items-center justify-between gap-3",
  interviewRow:
    "grid min-h-[46px] gap-2 rounded-xl border border-zinc-200/80 bg-white p-1.5 md:grid-cols-[1.2fr_1fr_1fr_36px] md:items-center",
  interviewStatusSlot:
    "inline-flex h-4 w-4 shrink-0 items-center justify-center",
  interviewStatusIcon:
    "h-4 w-4 text-emerald-600",
  topBorderRow:
    "mt-2.5 flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 pt-2.5",
  undoRowSoft:
    "grid min-h-[46px] gap-2 rounded-xl border border-violet-100 bg-white/80 p-1.5 md:grid-cols-[1.2fr_1fr_1fr_36px] md:items-center",
  undoContent:
    "flex min-h-10 min-w-0 items-center gap-2 px-1 text-sm text-zinc-700 md:col-span-3",
};

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import type {
  DashboardApplicationItem,
  DashboardInterviewItem,
} from "@/lib/api";
import { buttonStyles, formStyles, sectionStyles, textStyles } from "@/lib/styles";
import {
  mapInterviewTypeLabel,
  mapWorkModeLabel,
} from "../../applications/helpers/application-labels";
import {
  formatWaitingLabel,
  formatWaitingResultLabel,
  getUpcomingInterviewTiming,
  getWaitingUrgencyTone,
  getWholeCalendarDaysBetween,
  joinMetadata,
  type UpcomingTone,
  type WaitingTone,
} from "../helpers/dashboard-card-formatters";

type BaseProps = {
  count: number;
  errorMessage: string | null;
  hasMore: boolean;
  helperText: string;
  isLoading: boolean;
  isLoadingMore: boolean;
  now: Date;
  pendingIds: ReadonlySet<string>;
  title: string;
  onLoadMore: () => void;
  onMarkFollowedUp: (applicationId: string, interviewId?: string) => void;
  onUndo: (id: string) => void;
};

type ApplicationProps = BaseProps & {
  followUpAfterApplyingDays: number;
  items: DashboardApplicationItem[];
  kind: "applications";
};

type UpcomingInterviewProps = BaseProps & {
  items: DashboardInterviewItem[];
  kind: "upcoming-interviews";
};

type InterviewFollowUpProps = BaseProps & {
  followUpAfterInterviewDays: number;
  items: DashboardInterviewItem[];
  kind: "interviews-to-follow-up";
};

export type DashboardActionModuleProps =
  | ApplicationProps
  | UpcomingInterviewProps
  | InterviewFollowUpProps;

const chipBaseStyles = "rounded-full px-3 py-1 text-xs font-medium";

const waitingToneStyles: Record<WaitingTone, string> = {
  yellow: "bg-yellow-100 text-yellow-800",
  amber: "bg-amber-100 text-amber-800",
  orange: "bg-orange-100 text-orange-800",
  red: "bg-red-100 text-red-800",
};

const upcomingToneStyles: Record<UpcomingTone, string> = {
  future: "bg-emerald-50 text-emerald-700",
  tomorrow: "bg-emerald-100 text-emerald-800",
  today: "bg-emerald-600 text-white",
};

const applicationHref = (applicationId: string): string =>
  `/applications?id=${encodeURIComponent(applicationId)}`;

export const DashboardActionModule = (props: DashboardActionModuleProps) => {
  const maxApplicationWaitingDays = props.kind === "applications"
    ? Math.max(...props.items.map((item) => getWholeCalendarDaysBetween(
      item.appliedAt ?? item.createdAt,
      props.now,
    )))
    : null;
  const maxInterviewWaitingDays = props.kind === "interviews-to-follow-up"
    ? Math.max(...props.items.map((item) => getWholeCalendarDaysBetween(
      item.scheduledAt!,
      props.now,
    )))
    : null;

  return <Card className="flex flex-col self-start p-4">
    <div className={sectionStyles.splitRow}>
      <div>
        <h2 className={textStyles.sectionTitle}>{props.title}</h2>
        <p className={textStyles.description}>{props.helperText}</p>
      </div>
      <span className={buttonStyles.pillAccent}>
        {props.isLoading ? "..." : props.count}
      </span>
    </div>

    {props.errorMessage && <div className={`mt-4 ${formStyles.error}`}>{props.errorMessage}</div>}
    {props.isLoading && <p className="mt-4 text-sm text-zinc-700">Loading action items...</p>}

    {!props.isLoading && props.items.length === 0 && (
      <EmptyState
        className="mt-4"
        compact
        description="Nothing needs your attention in this section."
        title="All clear"
      />
    )}

    {!props.isLoading && props.items.length > 0 && (
      <ul className="mt-4 space-y-3">
        {props.kind === "applications"
          ? props.items.map((item) => (
            <ApplicationActionItem
              item={item}
              key={item.applicationId}
              maxWaitingDays={maxApplicationWaitingDays!}
              minWaitingDays={props.followUpAfterApplyingDays}
              now={props.now}
              pending={props.pendingIds.has(item.applicationId)}
              onMarkFollowedUp={props.onMarkFollowedUp}
              onUndo={props.onUndo}
            />
          ))
          : props.items.map((item) => (
            <InterviewActionItem
              item={item}
              key={item.interviewId}
              kind={props.kind}
              maxWaitingDays={maxInterviewWaitingDays}
              minWaitingDays={props.kind === "interviews-to-follow-up"
                ? props.followUpAfterInterviewDays
                : null}
              now={props.now}
              pending={props.pendingIds.has(item.interviewId)}
              showFollowUp={props.kind === "interviews-to-follow-up"}
              onMarkFollowedUp={props.onMarkFollowedUp}
              onUndo={props.onUndo}
            />
          ))}
      </ul>
    )}

    {props.hasMore && (
      <div className="mt-4 border-t border-zinc-100 pt-4">
        <Button
          disabled={props.isLoadingMore}
          onClick={props.onLoadMore}
          variant="secondarySoft"
        >
          {props.isLoadingMore ? "Loading..." : "Load more"}
        </Button>
      </div>
    )}
  </Card>;
};

const ApplicationActionItem = ({
  item,
  maxWaitingDays,
  minWaitingDays,
  now,
  pending,
  onMarkFollowedUp,
  onUndo,
}: {
  item: DashboardApplicationItem;
  maxWaitingDays: number;
  minWaitingDays: number;
  now: Date;
  pending: boolean;
  onMarkFollowedUp: BaseProps["onMarkFollowedUp"];
  onUndo: BaseProps["onUndo"];
}) => {
  const waitingDays = getWholeCalendarDaysBetween(item.appliedAt ?? item.createdAt, now);
  const waitingTone = getWaitingUrgencyTone(waitingDays, minWaitingDays, maxWaitingDays);
  const metadata = joinMetadata([
    item.location,
    mapWorkModeLabel(item.workMode),
  ]);

  return (
    <li className={`rounded-xl border border-zinc-200 bg-white p-3 ${pending ? "opacity-60" : ""}`}>
      <div className="flex flex-wrap gap-2">
        <span className={`${chipBaseStyles} ${waitingToneStyles[waitingTone]}`}>
          {formatWaitingLabel(waitingDays)}
        </span>
      </div>
      <div className="mt-2 min-w-0">
        <p className={textStyles.strong}>{item.companyName}</p>
        <p className={textStyles.muted}>{item.positionTitle}</p>
      </div>
      {metadata && <p className={textStyles.timestamp}>{metadata}</p>}
      <ItemActions
        applicationId={item.applicationId}
        pending={pending}
        onMark={() => onMarkFollowedUp(item.applicationId)}
        onUndo={() => onUndo(item.applicationId)}
      />
    </li>
  );
};

const InterviewActionItem = ({
  item,
  kind,
  maxWaitingDays,
  minWaitingDays,
  now,
  pending,
  showFollowUp,
  onMarkFollowedUp,
  onUndo,
}: {
  item: DashboardInterviewItem;
  kind: "upcoming-interviews" | "interviews-to-follow-up";
  maxWaitingDays: number | null;
  minWaitingDays: number | null;
  now: Date;
  pending: boolean;
  showFollowUp: boolean;
  onMarkFollowedUp: BaseProps["onMarkFollowedUp"];
  onUndo: BaseProps["onUndo"];
}) => {
  const metadata = joinMetadata([
    item.location,
    mapWorkModeLabel(item.workMode),
  ]);
  const waitingDays = showFollowUp
    ? getWholeCalendarDaysBetween(item.scheduledAt!, now)
    : null;
  const timing = showFollowUp
    ? {
      label: formatWaitingResultLabel(waitingDays!),
      tone: getWaitingUrgencyTone(waitingDays!, minWaitingDays!, maxWaitingDays!),
    }
    : getUpcomingInterviewTiming(item.scheduledAt!, now);
  const timingStyles = kind === "interviews-to-follow-up"
    ? waitingToneStyles[timing.tone as WaitingTone]
    : upcomingToneStyles[timing.tone as UpcomingTone];

  return (
    <li className={`rounded-xl border border-zinc-200 bg-white p-3 ${pending ? "opacity-60" : ""}`}>
      <div className="flex flex-wrap gap-2">
        <span className={buttonStyles.pill}>{mapInterviewTypeLabel(item.interviewType)}</span>
        <span className={`${chipBaseStyles} ${timingStyles}`}>{timing.label}</span>
      </div>
      <div className="mt-2 min-w-0">
        <p className={textStyles.strong}>{item.companyName}</p>
        <p className={textStyles.muted}>{item.positionTitle}</p>
      </div>
      {metadata && <p className={textStyles.timestamp}>{metadata}</p>}
      <ItemActions
        applicationId={item.applicationId}
        pending={pending}
        showFollowUp={showFollowUp}
        onMark={() => onMarkFollowedUp(item.applicationId, item.interviewId)}
        onUndo={() => onUndo(item.interviewId)}
      />
    </li>
  );
};

const ItemActions = ({
  applicationId,
  pending,
  showFollowUp = true,
  onMark,
  onUndo,
}: {
  applicationId: string;
  pending: boolean;
  showFollowUp?: boolean;
  onMark: () => void;
  onUndo: () => void;
}) => (
  <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-zinc-100 pt-2.5">
    <Link
      className="inline-flex h-8 shrink-0 items-center rounded-lg px-2.5 text-xs font-medium text-violet-700 transition hover:bg-violet-50 hover:text-violet-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-500"
      href={applicationHref(applicationId)}
    >
      View application
    </Link>
    {showFollowUp &&
      (pending ? (
        <div className="flex items-center gap-2 whitespace-nowrap text-sm text-zinc-700">
          <span>Marked followed up</span>
          <button className={buttonStyles.link} onClick={onUndo} type="button">
            Undo
          </button>
        </div>
      ) : (
        <Button
          className="h-8 shrink-0 whitespace-nowrap text-xs"
          onClick={onMark}
          variant="ghost"
        >
          Mark followed up
        </Button>
      ))}
  </div>
);

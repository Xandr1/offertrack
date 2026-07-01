import Link from "next/link";
import { CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type {
  DashboardApplicationItem,
  DashboardInterviewItem,
} from "@/lib/api";
import { buttonStyles, formStyles, sectionStyles, textStyles } from "@/lib/styles";
import { formatDateTime } from "@/lib/date-format";
import { formatUpdatedAtRelative } from "../../applications/helpers/application-date-helpers";
import {
  interviewStatusLabels,
  mapInterviewTypeLabel,
  mapWorkModeLabel,
} from "../../applications/helpers/application-labels";

type BaseProps = {
  count: number;
  errorMessage: string | null;
  hasMore: boolean;
  helperText: string;
  isLoading: boolean;
  isLoadingMore: boolean;
  pendingIds: ReadonlySet<string>;
  title: string;
  onLoadMore: () => void;
  onMarkFollowedUp: (applicationId: string, interviewId?: string) => void;
  onUndo: (id: string) => void;
};

type ApplicationProps = BaseProps & {
  items: DashboardApplicationItem[];
  kind: "applications";
};

type InterviewProps = BaseProps & {
  items: DashboardInterviewItem[];
  kind: "upcoming-interviews" | "interviews-to-follow-up";
};

export type DashboardActionModuleProps = ApplicationProps | InterviewProps;

const applicationHref = (applicationId: string): string =>
  `/applications?id=${encodeURIComponent(applicationId)}`;

export const DashboardActionModule = (props: DashboardActionModuleProps) => (
  <Card className="flex flex-col self-start">
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
      <div className="mt-4">
        <div className={sectionStyles.dashedEmpty}>
          <div className="flex items-center justify-center gap-2 text-sm font-medium text-emerald-700">
            <CheckCircle2 aria-hidden className="h-4 w-4" />
            <p>All clear — no action needed</p>
          </div>
        </div>
      </div>
    )}

    {!props.isLoading && props.items.length > 0 && (
      <ul className="mt-4 space-y-3">
        {props.kind === "applications"
          ? props.items.map((item) => (
            <ApplicationActionItem
              item={item}
              key={item.applicationId}
              pending={props.pendingIds.has(item.applicationId)}
              onMarkFollowedUp={props.onMarkFollowedUp}
              onUndo={props.onUndo}
            />
          ))
          : props.items.map((item) => (
            <InterviewActionItem
              item={item}
              key={item.interviewId}
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
          variant="secondary"
        >
          {props.isLoadingMore ? "Loading..." : "Load more"}
        </Button>
      </div>
    )}
  </Card>
);

const ApplicationActionItem = ({
  item,
  pending,
  onMarkFollowedUp,
  onUndo,
}: {
  item: DashboardApplicationItem;
  pending: boolean;
  onMarkFollowedUp: BaseProps["onMarkFollowedUp"];
  onUndo: BaseProps["onUndo"];
}) => {
  const details = [
    item.location,
    mapWorkModeLabel(item.workMode),
    item.appliedAt ? `Applied ${formatDateTime(item.appliedAt)}` : formatUpdatedAtRelative(item.updatedAt),
  ].filter(Boolean);

  return (
    <li className={`rounded-xl border border-zinc-200 bg-white px-4 py-3 ${pending ? "opacity-60" : ""}`}>
      <div className="min-w-0">
        <p className={textStyles.strong}>{item.companyName}</p>
        <p className={textStyles.muted}>{item.positionTitle}</p>
      </div>
      {details.length > 0 && <p className={textStyles.timestamp}>{details.join(" · ")}</p>}
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
  pending,
  showFollowUp,
  onMarkFollowedUp,
  onUndo,
}: {
  item: DashboardInterviewItem;
  pending: boolean;
  showFollowUp: boolean;
  onMarkFollowedUp: BaseProps["onMarkFollowedUp"];
  onUndo: BaseProps["onUndo"];
}) => {
  const details = [
    item.scheduledAt ? formatDateTime(item.scheduledAt) : null,
    item.location,
    mapWorkModeLabel(item.workMode),
  ].filter(Boolean);

  return (
    <li className={`rounded-xl border border-zinc-200 bg-white px-4 py-3 ${pending ? "opacity-60" : ""}`}>
      <div className="flex flex-wrap gap-2">
        <span className={buttonStyles.pill}>{mapInterviewTypeLabel(item.interviewType)}</span>
        <span className={buttonStyles.pill}>{interviewStatusLabels[item.status]}</span>
      </div>
      <div className="mt-2 min-w-0">
        <p className={textStyles.strong}>{item.companyName}</p>
        <p className={textStyles.muted}>{item.positionTitle}</p>
      </div>
      {details.length > 0 && <p className={textStyles.timestamp}>{details.join(" · ")}</p>}
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
  <div className="mt-4 flex h-8 items-end flex-nowrap gap-4 border-t border-zinc-100">
    <Link
      className={`${buttonStyles.link} inline-flex h-6 shrink-0 items-center whitespace-nowrap`}
      href={applicationHref(applicationId)}
    >
      View application
    </Link>
    {showFollowUp &&
      (pending ? (
        <div className="flex h-5 items-center whitespace-nowrap text-sm">
          <span>Marked followed up</span>
          <button className={buttonStyles.link} onClick={onUndo} type="button">Undo</button>
        </div>
      ) : (
        <Button
          className="inline-flex h-6 shrink-0 items-center justify-center whitespace-nowrap px-2 py-0 text-xs leading-none"
          onClick={onMark}
          variant="secondary"
        >
          Mark followed up
        </Button>
      ))}
  </div>
);

import Link from "next/link";
import { CheckCircle2 } from "lucide-react";
import { Card } from "@/components/ui/card";
import {
  DashboardApplicationItem,
  DashboardInterviewItem,
} from "@/lib/api";
import { buttonStyles, sectionStyles, textStyles } from "@/lib/styles";
import { formatDateTime } from "@/lib/date-format";
import { formatUpdatedAtRelative } from "../../applications/helpers/application-date-helpers";
import {
  applicationStageLabels,
  interviewStatusLabels,
  mapInterviewTypeLabel,
  mapWorkModeLabel,
} from "../../applications/helpers/application-labels";

type DashboardActionModuleBaseProps = {
  count: number;
  helperText: string;
  isLoading: boolean;
  title: string;
  viewAllHref: string;
};

type DashboardApplicationModuleProps = DashboardActionModuleBaseProps & {
  items: DashboardApplicationItem[];
  kind: "applications";
};

type DashboardInterviewModuleProps = DashboardActionModuleBaseProps & {
  items: DashboardInterviewItem[];
  kind: "interviews";
};

export type DashboardActionModuleProps =
  | DashboardApplicationModuleProps
  | DashboardInterviewModuleProps;

export const DashboardActionModule = ({
  count,
  helperText,
  isLoading,
  items,
  kind,
  title,
  viewAllHref,
}: DashboardActionModuleProps) => {
  return (
    <Card className="flex min-h-full flex-col">
      <div className={sectionStyles.splitRow}>
        <div>
          <h2 className={textStyles.sectionTitle}>{title}</h2>
          <p className={textStyles.description}>{helperText}</p>
        </div>
        <span className={buttonStyles.pillAccent}>{isLoading ? "..." : count}</span>
      </div>

      {isLoading && (
        <p className="mt-4 text-sm text-zinc-700">Loading action items...</p>
      )}

      {!isLoading && items.length === 0 && (
        <div className="mt-4">
          <div className={sectionStyles.dashedEmpty}>
            <div className="flex items-center justify-center gap-2 text-sm font-medium text-emerald-700">
              <CheckCircle2 aria-hidden className="h-4 w-4" />
              <p>All clear — no action needed</p>
            </div>
          </div>
        </div>
      )}

      {!isLoading && items.length > 0 && (
        <ul className="mt-4 space-y-3">
          {items.map((item) =>
            kind === "applications" ? (
              <ApplicationActionItem
                item={item as DashboardApplicationItem}
                key={(item as DashboardApplicationItem).applicationId}
              />
            ) : (
              <InterviewActionItem
                item={item as DashboardInterviewItem}
                key={(item as DashboardInterviewItem).interviewId}
              />
            ),
          )}
        </ul>
      )}

      <div className="mt-auto border-t border-zinc-100 pt-4">
        <Link className={buttonStyles.link} href={viewAllHref}>
          View all →
        </Link>
      </div>
    </Card>
  );
};

type ApplicationActionItemProps = {
  item: DashboardApplicationItem;
};

const ApplicationActionItem = ({ item }: ApplicationActionItemProps) => {
  const workModeLabel = mapWorkModeLabel(item.workMode);
  const details = [
    item.location,
    workModeLabel,
    formatUpdatedAtRelative(item.updatedAt),
  ].filter(Boolean);

  return (
    <li className="grid min-h-[172px] grid-rows-[auto_1fr_auto] rounded-xl border border-zinc-200 bg-white px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className={textStyles.strong}>{item.companyName}</p>
          <p className={textStyles.muted}>{item.positionTitle}</p>
        </div>
        <span className={buttonStyles.pill}>
          {applicationStageLabels[item.stage]}
        </span>
      </div>

      {details.length > 0 && (
        <p className={`${textStyles.timestamp} self-start`}>
          {details.join(" · ")}
        </p>
      )}

      <DashboardItemActions jobUrl={item.jobUrl} />
    </li>
  );
};

type InterviewActionItemProps = {
  item: DashboardInterviewItem;
};

const InterviewActionItem = ({ item }: InterviewActionItemProps) => {
  const workModeLabel = mapWorkModeLabel(item.workMode);
  const details = [
    item.scheduledAt ? `Scheduled ${formatDateTime(item.scheduledAt)}` : null,
    item.location,
    workModeLabel,
  ].filter(Boolean);

  return (
    <li className="grid min-h-[172px] grid-rows-[auto_1fr_auto] rounded-xl border border-zinc-200 bg-white px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className={textStyles.strong}>{item.companyName}</p>
          <p className={textStyles.muted}>{item.positionTitle}</p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
          <span className={buttonStyles.pill}>
            {mapInterviewTypeLabel(item.interviewType)}
          </span>
          <span className={buttonStyles.pill}>
            {interviewStatusLabels[item.status]}
          </span>
        </div>
      </div>

      {details.length > 0 && (
        <p className={`${textStyles.timestamp} self-start`}>
          {details.join(" · ")}
        </p>
      )}

      <DashboardItemActions jobUrl={item.jobUrl} />
    </li>
  );
};

type DashboardItemActionsProps = {
  jobUrl: string | null;
};

const DashboardItemActions = ({ jobUrl }: DashboardItemActionsProps) => {
  return (
    <div className="mt-4 flex min-h-6 items-center gap-4 border-t border-zinc-100 pt-3">
      <Link className={`${buttonStyles.link} whitespace-nowrap`} href="/applications">
        View in applications
      </Link>
      {jobUrl && (
        <a
          className="whitespace-nowrap text-sm font-medium text-violet-700 underline transition hover:text-violet-900"
          href={jobUrl}
          target="_blank"
          rel="noopener noreferrer"
        >
          Open job post
        </a>
      )}
    </div>
  );
};

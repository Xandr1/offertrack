"use client";

import { Application, ApplicationStage, InterviewStatus } from "@/lib/api";
import { formatDateTime } from "@/lib/date-format";
import {
  buttonStyles,
  cardStyles,
  formStyles,
  sectionStyles,
  textStyles,
} from "@/lib/styles";
import { formatUpdatedAtRelative } from "../helpers/application-date-helpers";
import {
  interviewStatusLabels,
  mapInterviewTypeLabel,
  mapWorkModeLabel,
} from "../helpers/application-labels";
import { interviewStatuses } from "../helpers/constants";
import { ApplicationStageSelect } from "./application-stage-select";
import { IconCalendar, IconPencil, IconTrash } from "./ui-icons";

type ApplicationCardProps = {
  application: Application;
  isDeleting: boolean;
  isUpdatingInterviewStatus: boolean;
  isUpdatingStage: boolean;
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
  onNextInterviewStatusChange: (
    application: Application,
    status: InterviewStatus,
  ) => void;
  onStageChange: (application: Application, stage: ApplicationStage) => void;
};

export const ApplicationCard = ({
  application,
  isDeleting,
  isUpdatingInterviewStatus,
  isUpdatingStage,
  onDelete,
  onEdit,
  onNextInterviewStatusChange,
  onStageChange,
}: ApplicationCardProps) => {
  const isBusy = isDeleting || isUpdatingInterviewStatus || isUpdatingStage;
  const nextInterview = application.nextInterview;
  const workModeLabel = mapWorkModeLabel(application.workMode);
  const hasEmeraldAccent = Boolean(nextInterview);
  const nextInterviewLabel = nextInterview
    ? nextInterview.scheduledAt
      ? `Next interview: ${mapInterviewTypeLabel(nextInterview.type)} · ${formatDateTime(nextInterview.scheduledAt)}`
      : `Next interview: ${mapInterviewTypeLabel(nextInterview.type)}`
    : "No upcoming interview";

  return (
    <article className={cardStyles.application}>
      <div className={sectionStyles.cardTopRow}>
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-zinc-950">
            {application.companyName}
          </h2>
          <p className="truncate text-sm text-zinc-700">
            <span className="font-bold text-zinc-950">{application.positionTitle}</span> {application.location && ` · ${application.location}`}{workModeLabel && ` · ${workModeLabel}`}
          </p>
        </div>

        <ApplicationStageSelect
          disabled={isBusy}
          showLabel={false}
          stage={application.stage}
          selectClassName="h-8 w-40 rounded-lg border-zinc-300 bg-white px-2.5 text-sm font-medium text-zinc-900"
          wrapperClassName="mr-[5px] shrink-0"
          onChange={(stage) => onStageChange(application, stage)}
        />
      </div>

      <div
        className={`${sectionStyles.interviewHighlight} ${hasEmeraldAccent
          ? "border-emerald-100 bg-emerald-50 text-emerald-950"
          : "border-zinc-200 bg-zinc-50 text-zinc-900"
          }`}
      >
        <div className="flex min-w-0 items-center gap-2">
          <IconCalendar
            className={`h-4 w-4 shrink-0 ${hasEmeraldAccent ? "text-emerald-600" : "text-zinc-500"
              }`}
          />
          <span className="truncate text-zinc-800">
            {nextInterviewLabel}
          </span>
        </div>

        {nextInterview && (
          <select
            className={formStyles.interviewStatusSelect}
            disabled={isBusy}
            value={nextInterview.status}
            onChange={(event) => {
              onNextInterviewStatusChange(
                application,
                event.target.value as InterviewStatus,
              );
            }}
          >
            {interviewStatuses.map((status) => (
              <option key={status} value={status}>
                {interviewStatusLabels[status]}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className={sectionStyles.topBorderRow}>
        <p className={textStyles.tinyMuted}>{formatUpdatedAtRelative(application.updatedAt)}</p>
        <div className="flex items-center gap-2">
          <button
            className={buttonStyles.ghost}
            disabled={isBusy}
            onClick={() => onEdit(application)}
            type="button"
          >
            <IconPencil className="mr-1.5 h-4 w-4" />
            Edit application
          </button>
          <button
            className={buttonStyles.ghostDanger}
            disabled={isBusy}
            onClick={() => onDelete(application)}
            type="button"
          >
            <IconTrash className="mr-1.5 h-4 w-4" />
            Delete
          </button>
        </div>
      </div>
    </article>
  );
};

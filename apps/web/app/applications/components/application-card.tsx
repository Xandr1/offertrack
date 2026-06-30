"use client";

import { Application, ApplicationStage, InterviewStatus } from "@/lib/api";
import { Card } from "@/components/ui/card";
import { Select } from "@/components/ui/input";
import { formatDateTime } from "@/lib/date-format";
import {
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
import { ApplicationCardActions } from "./application-card-actions";
import { ApplicationStageSelect } from "./application-stage-select";
import { IconCalendar } from "./ui-icons";

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
  const interview = application.nextInterview ?? application.lastInterview;
  const isLastInterview = !application.nextInterview && Boolean(application.lastInterview);
  const workModeLabel = mapWorkModeLabel(application.workMode);
  const hasMetadata = Boolean(application.location || workModeLabel);
  const hasEmeraldAccent = Boolean(application.nextInterview);
  const interviewLabel = interview
    ? interview.scheduledAt
      ? `${isLastInterview ? "Last" : "Next"} interview: ${mapInterviewTypeLabel(interview.type)} · ${formatDateTime(interview.scheduledAt)}`
      : `${isLastInterview ? "Last" : "Next"} interview: ${mapInterviewTypeLabel(interview.type)}`
    : "No upcoming interview";

  return (
    <Card as="article" variant="application">
      <div className="xl:grid xl:grid-cols-[minmax(0,1fr)_minmax(190px,220px)] xl:items-start xl:gap-3">
        <div className="min-w-0">
          <div className={sectionStyles.cardTopRow}>
            <div className="min-w-0">
              <h2 className="truncate text-base font-semibold text-zinc-950">
                {application.companyName}
              </h2>
              <p className="truncate text-sm text-zinc-700">
                <span className="font-bold text-zinc-950">
                  {application.positionTitle}
                </span>
              </p>
              {hasMetadata && (
                <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-zinc-700">
                  {application.location && <span>{application.location}</span>}
                  {workModeLabel && <span>{workModeLabel}</span>}
                </div>
              )}
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
            className={`${sectionStyles.interviewHighlight} xl:mt-2 ${hasEmeraldAccent
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
                {interviewLabel}
              </span>
            </div>
            {interview?.status === "scheduled" && (
              <Select
                disabled={isBusy}
                value={interview.status}
                variant="interviewStatus"
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
              </Select>
            )}
          </div>
        </div>

        <div className="hidden min-h-full flex-col items-start justify-between border-l border-zinc-100 pl-3 xl:flex xl:self-stretch">
          <ApplicationCardActions
            application={application}
            className="flex flex-col items-start gap-1"
            editLabel="Edit application"
            isBusy={isBusy}
            onDelete={onDelete}
            onEdit={onEdit}
          />
          <p className={`${textStyles.tinyMuted} px-2.5 pt-1`}>
            {formatUpdatedAtRelative(application.updatedAt)}
          </p>
        </div>
      </div>

      <div className={`${sectionStyles.topBorderRow} xl:hidden`}>
        <p className={textStyles.tinyMuted}>
          {formatUpdatedAtRelative(application.updatedAt)}
        </p>
        <ApplicationCardActions
          application={application}
          className="flex flex-wrap items-center justify-end gap-2"
          editLabel="Edit"
          isBusy={isBusy}
          onDelete={onDelete}
          onEdit={onEdit}
        />
      </div>
    </Card>
  );
};

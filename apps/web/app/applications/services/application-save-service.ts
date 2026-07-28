import {
  Application,
  ApplicationInterview,
  createApplication,
  replaceApplication,
} from "@/lib/api";
import {
  toCreatePayload,
  toReplacePayload,
} from "../helpers/application-form-mappers";
import {
  toCreateInterviewPayload,
  toReplaceInterviewPayload,
} from "../helpers/interview-form-mappers";
import { ApplicationFormState } from "../models/application-form-model";
import { InterviewDraftRow } from "../models/interview-row-model";

type SaveCreateInput = {
  form: ApplicationFormState;
  mode: "create";
  rows: InterviewDraftRow[];
};

type SaveEditInput = {
  applicationId: string;
  form: ApplicationFormState;
  mode: "edit";
  rows: InterviewDraftRow[];
  pendingUndoRowIds?: string[];
};

type SaveApplicationWithInterviewsInput = SaveCreateInput | SaveEditInput;

export type SaveApplicationWithInterviewsResult = {
  application: Application;
  applicationId: string;
  interviews: ApplicationInterview[];
  mode: "create" | "edit";
};

type ApplicationSaveServiceDeps = {
  createApplication: typeof createApplication;
  replaceApplication: typeof replaceApplication;
};

const defaultDeps: ApplicationSaveServiceDeps = {
  createApplication,
  replaceApplication,
};

const buildCreateInterviewsPayload = (rows: InterviewDraftRow[]) => {
  return rows.map(toCreateInterviewPayload);
};

const buildReplaceInterviewsPayload = (
  rows: InterviewDraftRow[],
  pendingUndoRowIds: string[] = [],
) => {
  const pendingUndoRowIdSet = new Set(pendingUndoRowIds);

  return rows
    .filter((row) => !pendingUndoRowIdSet.has(row.rowId))
    .map(toReplaceInterviewPayload);
};

export const saveApplicationWithInterviews = async (
  input: SaveApplicationWithInterviewsInput,
  deps: ApplicationSaveServiceDeps = defaultDeps,
): Promise<SaveApplicationWithInterviewsResult> => {
  if (input.mode === "create") {
    const response = await deps.createApplication({
      ...toCreatePayload(input.form),
      interviews: buildCreateInterviewsPayload(input.rows),
    });

    return {
      application: response.application,
      applicationId: response.application.id,
      interviews: response.interviews,
      mode: "create",
    };
  }

  const response = await deps.replaceApplication(input.applicationId, {
    ...toReplacePayload(input.form),
    interviews: buildReplaceInterviewsPayload(
      input.rows,
      input.pendingUndoRowIds,
    ),
  });

  return {
    application: response.application,
    applicationId: input.applicationId,
    interviews: response.interviews,
    mode: "edit",
  };
};

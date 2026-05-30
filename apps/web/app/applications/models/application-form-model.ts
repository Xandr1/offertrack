import { ApplicationStage, WorkMode } from "@/lib/api";

export type ApplicationFormMode = "create" | "edit";

export type ApplicationFormState = {
  companyName: string;
  positionTitle: string;
  location: string;
  workMode: WorkMode | "";
  appliedAt: string;
  notes: string;
  stage: ApplicationStage;
};

export const initialApplicationFormState: ApplicationFormState = {
  companyName: "",
  positionTitle: "",
  location: "",
  workMode: "",
  appliedAt: "",
  notes: "",
  stage: "initial",
};

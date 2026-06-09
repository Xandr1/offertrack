export type SettingsFormState = {
  followUpAfterApplyingDays: string;
  upcomingInterviewDays: string;
  followUpAfterInterviewDays: string;
  targetRole: string;
};

export const initialSettingsFormState: SettingsFormState = {
  followUpAfterApplyingDays: "",
  upcomingInterviewDays: "",
  followUpAfterInterviewDays: "",
  targetRole: "",
};

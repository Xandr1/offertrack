import { request } from "./client";
import { settingsSchema } from "./schemas";
import { Settings, UpdateSettingsRequest } from "./types";

export const getSettings = (): Promise<Settings> => {
  return request<Settings>("/api/settings", settingsSchema);
};

export const updateSettings = (
  payload: UpdateSettingsRequest,
): Promise<Settings> => {
  return request<Settings>("/api/settings", settingsSchema, {
    body: JSON.stringify(payload),
    method: "PUT",
  });
};

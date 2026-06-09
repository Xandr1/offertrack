"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShellLayout } from "@/components/layout/shell-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Settings,
  getCurrentUser,
  getSettings,
  logout,
  updateSettings,
} from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import {
  getRequestErrorMessage,
  isAuthError,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { formStyles, layoutStyles, pageStyles, textStyles } from "@/lib/styles";
import { SettingsPageHeader } from "./components/settings-page-header";
import { SettingsForm } from "./components/settings-form";
import {
  getSettingsFormValidationError,
  toSettingsFormState,
  toUpdateSettingsPayload,
} from "./helpers/settings-form-mappers";
import { SettingsFormState } from "./models/settings-form-model";
import { invalidateSettingsFeatureQueries } from "./services/settings-invalidation";

export default function SettingsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const userQuery = useQuery({
    queryKey: queryKeys.authMe,
    queryFn: getCurrentUser,
    retry: false,
  });

  const settingsQuery = useQuery({
    queryKey: queryKeys.settings,
    queryFn: getSettings,
    retry: false,
    enabled: Boolean(userQuery.data),
  });

  const shouldRedirectToLogin = isAuthError(userQuery.error);

  useEffect(() => {
    if (!userQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(userQuery.error, router);
  }, [router, userQuery.error]);

  async function handleLogout() {
    try {
      await logout();
    } finally {
      queryClient.clear();
      router.replace("/login");
    }
  }

  if (userQuery.isPending) {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>Loading settings...</div>
      </main>
    );
  }

  if (shouldRedirectToLogin) {
    return null;
  }

  if (userQuery.error) {
    return (
      <main className={pageStyles.centered}>
        <Card variant="auth">
          <h1 className={textStyles.pageTitle}>Settings unavailable</h1>
          <div className="mt-4">
            <div className={formStyles.error}>
              {getRequestErrorMessage(userQuery.error)}
            </div>
          </div>
          <Button
            className="mt-4"
            onClick={() => userQuery.refetch()}
            variant="secondary"
          >
            Retry
          </Button>
        </Card>
      </main>
    );
  }

  if (!userQuery.data) {
    return null;
  }

  const user = userQuery.data;

  return (
    <ShellLayout activeRoute="/settings">
      <div className={layoutStyles.container}>
        <SettingsPageHeader email={user.email} onSignOut={handleLogout} />

        <section className={layoutStyles.section}>
          {settingsQuery.isPending && !settingsQuery.data ? (
            <Card>
              <div className={pageStyles.statusMessage}>Loading settings...</div>
            </Card>
          ) : settingsQuery.error ? (
            <Card>
              <h2 className={textStyles.sectionTitle}>Settings unavailable</h2>
              <div className="mt-4">
                <div className={formStyles.error}>
                  {getRequestErrorMessage(settingsQuery.error)}
                </div>
              </div>
              <Button
                className="mt-4"
                onClick={() => settingsQuery.refetch()}
                variant="secondary"
              >
                Retry
              </Button>
            </Card>
          ) : settingsQuery.data ? (
            <SettingsEditor settings={settingsQuery.data} />
          ) : (
            <Card>
              <div className={pageStyles.statusMessage}>Loading settings...</div>
            </Card>
          )}
        </section>
      </div>
    </ShellLayout>
  );
}

type SettingsEditorProps = {
  settings: Settings;
};

const SettingsEditor = ({ settings }: SettingsEditorProps) => {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<SettingsFormState>(() =>
    toSettingsFormState(settings),
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const saveSettingsMutation = useMutation({
    mutationFn: updateSettings,
    onError: (error) => {
      void handleMutationError(error);
    },
    onSuccess: (updatedSettings) => {
      setForm(toSettingsFormState(updatedSettings));
      setFormError(null);
      setSuccessMessage("Settings saved.");
      invalidateSettingsFeatureQueries(queryClient);
    },
  });

  async function handleMutationError(error: unknown) {
    if (await redirectToLoginIfProtectedRoute(error, router)) {
      return;
    }

    setSuccessMessage(null);
    setFormError(getRequestErrorMessage(error));
  }

  function handleFieldChange<Key extends keyof SettingsFormState>(
    key: Key,
    value: SettingsFormState[Key],
  ) {
    setForm((currentForm) => ({
      ...currentForm,
      [key]: value,
    }));
    setFormError(null);
    setSuccessMessage(null);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const validationError = getSettingsFormValidationError(form);
    if (validationError) {
      setSuccessMessage(null);
      setFormError(validationError);
      return;
    }

    setFormError(null);
    setSuccessMessage(null);
    saveSettingsMutation.mutate(toUpdateSettingsPayload(form));
  }

  return (
    <SettingsForm
      disabled={saveSettingsMutation.isPending}
      errorMessage={formError}
      form={form}
      isSaving={saveSettingsMutation.isPending}
      successMessage={successMessage}
      onFieldChange={handleFieldChange}
      onSubmit={handleSubmit}
    />
  );
};

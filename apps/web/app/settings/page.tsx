"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useProtectedUser } from "@/components/auth/protected-route";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Settings,
  getSettings,
  logout,
  logoutAll,
  updateSettings,
} from "@/lib/api";
import type { UserSummary } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { useRedirectToLoginOnProtectedError } from "@/lib/auth/use-redirect-to-login-on-protected-error";
import {
  getRequestErrorMessage,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { formStyles, layoutStyles, modalStyles, pageStyles, textStyles } from "@/lib/styles";
import { ApplicationModal } from "../applications/components/application-modal";
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
  const user = useProtectedUser();

  return <SettingsPageContent user={user} />;
}

const SettingsPageContent = ({ user }: { user: UserSummary }) => {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [signedOut, setSignedOut] = useState(false);
  const settingsQuery = useQuery({
    enabled: !signedOut,
    queryKey: queryKeys.settings,
    queryFn: getSettings,
    retry: false,
  });
  const isRedirectingToLogin = useRedirectToLoginOnProtectedError(
    settingsQuery.error,
  );

  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  const [isLogoutAllConfirmationOpen, setIsLogoutAllConfirmationOpen] = useState(false);
  async function handleLogout(all = false) {
    setSigningOut(true);
    setLogoutError(null);
    try {
      await (all ? logoutAll() : logout());
      setSignedOut(true);
      clearAuthSessionQueries(queryClient);
      router.replace("/login");
    } catch (error) {
      setLogoutError(getRequestErrorMessage(error));
    } finally { setSigningOut(false); }
  }

  if (signedOut || isRedirectingToLogin) {
    return null;
  }

  return (
    <div className={layoutStyles.container}>
        <SettingsPageHeader email={user.email} disabled={signingOut}
          onSignOut={() => void handleLogout()} />
        {logoutError && <p role="alert" className={formStyles.error}>{logoutError}</p>}

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

        <section className={layoutStyles.section} aria-labelledby="settings-security-title">
          <Card className="max-w-4xl space-y-3 p-4 sm:p-5">
            <h2 className={textStyles.sectionTitle} id="settings-security-title">Security</h2>
            <p className={textStyles.description}>
              Sign out of OfferTrack on every device, including this one.
            </p>
            <Button disabled={signingOut} onClick={() => setIsLogoutAllConfirmationOpen(true)} variant="secondary">
              Sign out all devices
            </Button>
          </Card>
        </section>

        <ApplicationModal
          description="This will sign you out everywhere, including this device."
          initialFocusSelector="[data-logout-all-cancel]"
          isOpen={isLogoutAllConfirmationOpen}
          title="Sign out on all devices?"
          variant="compact"
          onClose={() => setIsLogoutAllConfirmationOpen(false)}
        >
          <div className={modalStyles.softFooterBleedCompact}>
            <Button data-logout-all-cancel onClick={() => setIsLogoutAllConfirmationOpen(false)} variant="secondarySoft">
              Cancel
            </Button>
            <Button onClick={() => {
              setIsLogoutAllConfirmationOpen(false);
              void handleLogout(true);
            }} variant="primarySoft">
              Sign out all devices
            </Button>
          </div>
        </ApplicationModal>
    </div>
  );
};

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
    if (await redirectToLoginIfProtectedRoute(error, router, queryClient)) {
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

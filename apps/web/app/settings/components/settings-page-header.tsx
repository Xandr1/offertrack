import { Button } from "@/components/ui/button";
import { layoutStyles, textStyles } from "@/lib/styles";

type SettingsPageHeaderProps = {
  email: string;
  onSignOut: () => void;
};

export const SettingsPageHeader = ({
  email,
  onSignOut,
}: SettingsPageHeaderProps) => (
  <header className={layoutStyles.splitHeader}>
    <div>
      <h1 className={textStyles.pageHeadline}>Settings</h1>
      <p className={textStyles.subtitle}>
        Configure your dashboard timing and target role.
      </p>
    </div>

    <div className="flex flex-wrap items-center gap-3 md:justify-end">
      <p className="break-all text-sm text-zinc-600">
        Signed in as <span className="font-medium text-zinc-950">{email}</span>
      </p>
      <Button onClick={onSignOut} variant="secondarySoft">
        Sign out
      </Button>
    </div>
  </header>
);

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
        Configure your dashboard timing and target role
      </p>
    </div>

    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-zinc-200 bg-white px-3 py-2 md:justify-end">
      <div className="min-w-0">
        <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">
          Account
        </p>
        <p className="break-all text-sm font-medium text-zinc-950">{email}</p>
      </div>
      <Button className="ml-auto" onClick={onSignOut} variant="ghost">
        Sign out
      </Button>
    </div>
  </header>
);

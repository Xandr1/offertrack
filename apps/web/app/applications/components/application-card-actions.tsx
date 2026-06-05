import { Application } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { IconPencil, IconTrash } from "./ui-icons";

type ApplicationCardActionsProps = {
  application: Application;
  className: string;
  editLabel: string;
  isBusy: boolean;
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
};

export const ApplicationCardActions = ({
  application,
  className,
  editLabel,
  isBusy,
  onDelete,
  onEdit,
}: ApplicationCardActionsProps) => {
  return (
    <div className={className}>
      {application.jobUrl && (
        <a
          className="inline-flex items-center rounded-lg px-2.5 py-1.5 text-sm font-medium text-violet-700 transition hover:bg-violet-50 hover:text-violet-900"
          href={application.jobUrl}
          target="_blank"
          rel="noopener noreferrer"
        >
          Open job post ↗
        </a>
      )}
      <Button
        disabled={isBusy}
        onClick={() => onEdit(application)}
        variant="ghost"
        type="button"
      >
        <IconPencil className="mr-1.5 h-4 w-4" />
        {editLabel}
      </Button>
      <Button
        disabled={isBusy}
        onClick={() => onDelete(application)}
        variant="ghostDanger"
        type="button"
      >
        <IconTrash className="mr-1.5 h-4 w-4" />
        Delete
      </Button>
    </div>
  );
};

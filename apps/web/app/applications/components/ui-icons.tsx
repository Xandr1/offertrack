import {
  Calendar,
  CheckCircle2,
  LucideIcon,
  LucideProps,
  Pencil,
  Plus,
  RotateCcw,
  Search,
  Sparkles,
  Trash2,
  X,
  Columns3,
  GripVertical,
  List,
} from "lucide-react";

const withDefaults = (Icon: LucideIcon) => {
  const WrappedIcon = ({ size = 16, strokeWidth = 2, ...props }: LucideProps) => (
    <Icon aria-hidden size={size} strokeWidth={strokeWidth} {...props} />
  );

  WrappedIcon.displayName = `Icon${Icon.displayName ?? Icon.name}`;

  return WrappedIcon;
};

export const IconPlus = withDefaults(Plus);
export const IconSparkles = withDefaults(Sparkles);
export const IconSearch = withDefaults(Search);
export const IconCalendar = withDefaults(Calendar);
export const IconPencil = withDefaults(Pencil);
export const IconTrash = withDefaults(Trash2);
export const IconCheckCircle = withDefaults(CheckCircle2);
export const IconUndoTimer = withDefaults(RotateCcw);
export const IconClose = withDefaults(X);
export const IconBoard = withDefaults(Columns3);
export const IconList = withDefaults(List);
export const IconGrip = withDefaults(GripVertical);

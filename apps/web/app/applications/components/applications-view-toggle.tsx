"use client";

import { Button } from "@/components/ui/button";
import type { ApplicationsView } from "../helpers/application-filters";
import { IconBoard, IconList } from "./ui-icons";

type ApplicationsViewToggleProps = {
  view: ApplicationsView;
  onChange: (view: ApplicationsView) => void;
};

export const ApplicationsViewToggle = ({
  view,
  onChange,
}: ApplicationsViewToggleProps) => (
  <div
    aria-label="Applications view"
    className="flex rounded-xl border border-zinc-200 bg-white p-1"
    role="group"
  >
    <Button
      aria-pressed={view === "list"}
      className="px-3 py-1.5"
      variant={view === "list" ? "secondarySoftAccent" : "ghost"}
      onClick={() => onChange("list")}
    >
      <IconList className="mr-1.5 h-4 w-4" />
      List
    </Button>
    <Button
      aria-pressed={view === "board"}
      className="px-3 py-1.5"
      variant={view === "board" ? "secondarySoftAccent" : "ghost"}
      onClick={() => onChange("board")}
    >
      <IconBoard className="mr-1.5 h-4 w-4" />
      Board
    </Button>
  </div>
);

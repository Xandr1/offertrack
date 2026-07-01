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
    className="flex h-10 items-center rounded-xl border border-zinc-200 bg-white p-1"
    role="group"
  >
    <Button
      aria-pressed={view === "list"}
      className="!h-8 px-3 py-0"
      variant={view === "list" ? "secondarySoftAccent" : "ghost"}
      onClick={() => onChange("list")}
    >
      <IconList className="mr-1.5 h-4 w-4" />
      List
    </Button>
    <Button
      aria-pressed={view === "board"}
      className="!h-8 px-3 py-0"
      variant={view === "board" ? "secondarySoftAccent" : "ghost"}
      onClick={() => onChange("board")}
    >
      <IconBoard className="mr-1.5 h-4 w-4" />
      Board
    </Button>
  </div>
);

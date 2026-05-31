"use client";

import { ShellLayout } from "@/components/layout/shell-layout";
import { Button } from "@/components/ui/button";
import {
  layoutStyles,
  textStyles,
  formStyles,
} from "@/lib/styles";
import { ApplicationModal } from "./components/application-modal";
import { ApplicationModalBody } from "./components/application-modal-body";
import { ApplicationToolbar } from "./components/application-toolbar";
import { ApplicationsList } from "./components/applications-list";
import { DeleteApplicationConfirmModal } from "./components/delete-application-confirm-modal";
import { IconPlus } from "./components/ui-icons";
import { useApplicationsPageController } from "./hooks/use-applications-page-controller";

const ApplicationsPage = () => {
  const controller = useApplicationsPageController();
  const { modalController } = controller;
  const isModalFormDisabled =
    modalController.isSaving ||
    (modalController.isEditMode && !modalController.interviewsLoadedForEdit);

  return (
    <ShellLayout activeRoute="/applications">
      <header className={layoutStyles.splitHeader}>
        <div>
          <h1 className={textStyles.pageHeadline}>Applications</h1>
          <p className={textStyles.subtitle}>
            Track roles and see what’s next.
          </p>
        </div>

        <Button
          onClick={controller.openCreateApplicationModal}
          variant="primarySoft"
        >
          <IconPlus className="mr-2 h-4 w-4" />
          Add application
        </Button>
      </header>

      <div className={layoutStyles.section}>
        <ApplicationToolbar
          searchInput={controller.searchInput}
          sort={controller.sort}
          stageFilter={controller.stageFilter}
          onSearchInputChange={controller.setSearchInput}
          onSortChange={(value) => controller.setFilters({ sort: value })}
          onStageChange={(value) => controller.setFilters({ stage: value })}
        />
      </div>

      {controller.pageError && (
        <section className="mt-5">
          <div className={formStyles.error}>{controller.pageError}</div>
        </section>
      )}

      <section className={layoutStyles.section}>
        <ApplicationsList
          applications={controller.filteredApplications}
          deletingApplicationId={
            controller.deleteApplicationMutation.isPending
              ? controller.deletingApplicationId
              : undefined
          }
          errorMessage={controller.listErrorMessage}
          isLoading={controller.applicationsQuery.isPending}
          nextInterviewStatusApplicationId={
            controller.updateInterviewStatusMutation.isPending
              ? controller.nextInterviewStatusApplicationId
              : undefined
          }
          onDelete={controller.handleDeleteRequest}
          onEdit={controller.openEditApplicationModal}
          onNextInterviewStatusChange={
            controller.handleNextInterviewStatusChange
          }
          onRetry={() => {
            void controller.applicationsQuery.refetch();
          }}
          onStageChange={controller.handleStageChange}
          stageUpdatingApplicationId={
            controller.updateStageMutation.isPending
              ? controller.stageUpdatingApplicationId
              : undefined
          }
        />
      </section>

      <ApplicationModal
        isCloseDisabled={modalController.isSaving}
        isOpen={modalController.isApplicationModalOpen}
        title={
          modalController.isCreateMode
            ? "Create application"
            : "Edit application"
        }
        onClose={controller.closeApplicationModal}
      >
        {modalController.formMode && (
          <ApplicationModalBody
            footerSection={{
              formMode: modalController.formMode,
              isSaveDisabled: controller.isModalSaveDisabled,
              isSubmitting: modalController.isSaving,
              modalError: modalController.saveError,
              onCancel: controller.closeApplicationModal,
            }}
            formSection={{
              disabled: isModalFormDisabled,
              form: modalController.form,
              onFieldChange: modalController.updateFormField,
            }}
            interviewsSection={{
              disabled: isModalFormDisabled,
              hasInvalidRow: modalController.hasInvalidRow,
              interviewsErrorMessage: modalController.interviewsError,
              isEditMode: modalController.isEditMode,
              isInterviewsLoading:
                modalController.isEditMode && modalController.isInterviewsLoading,
              pendingUndoRows: modalController.pendingUndoRows,
              rows: modalController.draftInterviewRows,
              onAddRow: modalController.addRow,
              onRemoveRow: modalController.removeRow,
              onRetryInterviews: () => {
                void controller.interviewsQuery.refetch();
              },
              onUndoRemoval: modalController.undoRowRemoval,
              onUpdateRow: modalController.updateRow,
            }}
            onSubmit={controller.handleSaveModal}
          />
        )}
      </ApplicationModal>

      <DeleteApplicationConfirmModal
        isDeleting={controller.deleteApplicationMutation.isPending}
        isOpen={Boolean(controller.applicationToDelete)}
        onCancel={() => {
          if (!controller.deleteApplicationMutation.isPending) {
            controller.setApplicationToDelete(null);
          }
        }}
        onConfirm={controller.handleDeleteConfirm}
      />
    </ShellLayout>
  );
};

export default ApplicationsPage;

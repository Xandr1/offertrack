"use client";

import { ShellLayout } from "@/components/layout/shell-layout";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { Button } from "@/components/ui/button";
import {
  layoutStyles,
  textStyles,
  formStyles,
} from "@/lib/styles";
import { ApplicationModal } from "./components/application-modal";
import { ApplicationAiDraftStep } from "./components/application-ai-draft-step";
import { ApplicationCreateMethodSwitch } from "./components/application-create-method-switch";
import { ApplicationModalBody } from "./components/application-modal-body";
import { ApplicationToolbar } from "./components/application-toolbar";
import { ApplicationsPagination } from "./components/applications-pagination";
import { ApplicationsList } from "./components/applications-list";
import { ApplicationsBoard } from "./components/applications-board";
import { DeleteApplicationConfirmModal } from "./components/delete-application-confirm-modal";
import { IconPlus } from "./components/ui-icons";
import { useApplicationsPageController } from "./hooks/use-applications-page-controller";

const ApplicationsPageContent = () => {
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

        <div className="flex flex-wrap items-center gap-2">
          <Button
            onClick={controller.openCreateApplicationModal}
            variant="primarySoft"
          >
            <IconPlus className="mr-2 h-4 w-4" />
            Add application
          </Button>
        </div>
      </header>

      <div className={layoutStyles.section}>
        <ApplicationToolbar
          direction={controller.direction}
          hasActiveFilters={controller.hasActiveFilters}
          searchInput={controller.searchInput}
          sort={controller.sort}
          stageFilter={controller.stageFilter}
          view={controller.view}
          onClearFilters={controller.clearFilters}
          onDirectionChange={(value) =>
            controller.setFilters({ direction: value })
          }
          onSearchClear={controller.clearSearch}
          onSearchInputChange={controller.setSearchInput}
          onSearchSubmit={controller.submitSearch}
          onSortChange={(value) => controller.setFilters({ sort: value })}
          onStageChange={(value) => controller.setFilters({ stage: value })}
          onViewChange={controller.setView}
        />
      </div>

      {controller.pageError && (
        <section className="mt-5">
          <div className={formStyles.error}>{controller.pageError}</div>
        </section>
      )}

      {controller.applicationDetailStatusMessage && (
        <section className="mt-5">
          {controller.applicationDetailStatusKind === "error" ? (
            <div className={formStyles.error}>
              {controller.applicationDetailStatusMessage}
            </div>
          ) : (
            <p className={textStyles.muted}>
              {controller.applicationDetailStatusMessage}
            </p>
          )}
        </section>
      )}

      <section className={layoutStyles.section}>
        {controller.view === "list" ? (
          <>
            <ApplicationsList
              applications={controller.applications}
              deletingApplicationId={
                controller.deleteApplicationMutation.isPending
                  ? controller.deletingApplicationId
                  : undefined
              }
              errorMessage={controller.listErrorMessage}
              hasActiveFilters={controller.hasActiveFilters}
              isLoading={controller.applicationsQuery.isPending}
              nextInterviewStatusApplicationId={
                controller.updateInterviewStatusMutation.isPending
                  ? controller.nextInterviewStatusApplicationId
                  : undefined
              }
              onDelete={controller.handleDeleteRequest}
              onClearFilters={controller.clearFilters}
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
            <ApplicationsPagination
              page={controller.page}
              totalPages={controller.totalPages}
              onPageChange={controller.setPage}
            />
          </>
        ) : (
          <ApplicationsBoard
            board={controller.boardController.boardQuery.data}
            errorMessage={controller.boardErrorMessage}
            isLoading={controller.boardController.boardQuery.isPending}
            isStageUpdatePending={
              controller.boardController.isStageUpdatePending
            }
            loadMoreState={controller.boardController.loadMoreState}
            onDragEnd={controller.boardController.handleDragEnd}
            onDelete={controller.handleDeleteRequest}
            onEdit={controller.openEditApplicationModal}
            onLoadMore={controller.boardController.loadMore}
            onRetry={() => {
              void controller.boardController.boardQuery.refetch();
            }}
          />
        )}
      </section>

      <ApplicationModal
        isCloseDisabled={modalController.isSaving}
        isOpen={modalController.isApplicationModalOpen}
        initialFocusSelector={
          modalController.isEditMode &&
          !modalController.interviewsLoadedForEdit
            ? undefined
            : modalController.isCreateMode &&
          modalController.createMethod === "ai" &&
          !modalController.hasGeneratedDraft
            ? "#application-ai-job-url"
            : "#application-company"
        }
        headerControls={
          modalController.isCreateMode && modalController.createMethod ? (
            <ApplicationCreateMethodSwitch
              disabled={controller.isCreateWithAiGenerating}
              method={modalController.createMethod}
              onChange={modalController.setCreateMethod}
            />
          ) : undefined
        }
        title={
          modalController.isCreateMode
            ? "Create application"
            : "Edit application"
        }
        onClose={controller.closeApplicationModal}
      >
        {modalController.isCreateMode &&
        modalController.createMethod === "ai" &&
        !modalController.hasGeneratedDraft ? (
          <ApplicationAiDraftStep
            errorMessage={controller.createWithAiError}
            isGenerating={controller.isCreateWithAiGenerating}
            jobUrl={controller.createWithAiJobUrl}
            onCancel={controller.closeApplicationModal}
            onJobUrlChange={controller.setCreateWithAiJobUrl}
            onSubmit={controller.handleCreateWithAiSubmit}
          />
        ) : modalController.formMode ? (
          <ApplicationModalBody
            footerSection={{
              formMode: modalController.formMode,
              isSaveDisabled: controller.isModalSaveDisabled,
              isSubmitting: modalController.isSaving,
              modalError: modalController.saveError,
              onCancel: controller.closeApplicationModal,
              onDelete:
                modalController.isEditMode &&
                controller.applicationDetailQuery.data
                  ? () => {
                      const application =
                        controller.applicationDetailQuery.data;
                      if (!application) {
                        return;
                      }

                      controller.closeApplicationModal();
                      controller.handleDeleteRequest(application);
                    }
                  : undefined,
            }}
            formSection={{
              disabled: isModalFormDisabled,
              form: modalController.form,
              onFieldChange: modalController.updateFormField,
            }}
            draftWarnings={modalController.draftWarnings}
            interviewsSection={{
              disabled: isModalFormDisabled,
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
        ) : null}
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

const ApplicationsPage = () => (
  <ProtectedRoute
    errorTitle="Applications unavailable"
    loadingLabel="Loading applications..."
  >
    {() => <ApplicationsPageContent />}
  </ProtectedRoute>
);

export default ApplicationsPage;

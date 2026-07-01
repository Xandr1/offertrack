"use client";

import { useEffect } from "react";
import { getRequestErrorMessage } from "@/lib/request-errors";
import type { useApplicationInterviewsQuery } from "./use-application-interviews-query";
import type { useApplicationModalController } from "./use-application-modal-controller";

type ApplicationInterviewsQuery = ReturnType<typeof useApplicationInterviewsQuery>;
type ApplicationModalController = ReturnType<typeof useApplicationModalController>;

type UseApplicationInterviewsModalLoaderParams = {
  interviewsQuery: ApplicationInterviewsQuery;
  modalController: ApplicationModalController;
};

export const useApplicationInterviewsModalLoader = ({
  interviewsQuery,
  modalController,
}: UseApplicationInterviewsModalLoaderParams) => {
  useEffect(() => {
    if (
      !modalController.isApplicationModalOpen ||
      !modalController.isEditMode ||
      !modalController.selectedApplicationId
    ) {
      return;
    }

    if (
      interviewsQuery.isPending &&
      !modalController.isInterviewsLoading &&
      !modalController.hasLoadedInterviews
    ) {
      modalController.markInterviewsLoading();
      return;
    }

    if (interviewsQuery.isError) {
      const nextError = getRequestErrorMessage(interviewsQuery.error);
      if (
        modalController.interviewsError !== nextError ||
        modalController.isInterviewsLoading
      ) {
        modalController.markInterviewsFailed(nextError);
      }
      return;
    }

    if (interviewsQuery.data && !modalController.hasLoadedInterviews) {
      modalController.markInterviewsLoaded(interviewsQuery.data);
    }
  }, [
    interviewsQuery.data,
    interviewsQuery.error,
    interviewsQuery.isError,
    interviewsQuery.isPending,
    modalController,
  ]);
};

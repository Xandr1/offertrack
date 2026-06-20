from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

WorkMode = Literal["remote", "hybrid", "onsite"]
InterviewType = Literal[
    "hr",
    "recruiter",
    "technical",
    "hiring_manager",
    "team_match",
    "home_assignment",
    "behavioral",
    "other",
]


class ParseJobRequest(BaseModel):
    job_url: str = Field(alias="jobUrl", min_length=1, max_length=2048)

    model_config = ConfigDict(populate_by_name=True)


class ExtractedInterview(BaseModel):
    type: InterviewType


class ExtractedDraft(BaseModel):
    company_name: str | None = None
    position_title: str | None = None
    location: str | None = None
    work_mode: WorkMode | None = None
    notes: str | None = None
    interviews: list[ExtractedInterview] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)

    @field_validator(
        "company_name", "position_title", "location", "work_mode", "notes", mode="before"
    )
    @classmethod
    def empty_string_to_none(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None

        return value

    @field_validator("warnings", mode="before")
    @classmethod
    def clean_warnings(cls, value: object) -> object:
        if value is None:
            return []

        if isinstance(value, list):
            return [item.strip() for item in value if isinstance(item, str) and item.strip()]

        return value


class DraftInterview(BaseModel):
    type: InterviewType
    status: Literal["planned"] = "planned"
    scheduled_at: None = Field(default=None, alias="scheduledAt")

    model_config = ConfigDict(populate_by_name=True)


class DraftResponse(BaseModel):
    company_name: str | None = Field(default=None, alias="companyName")
    position_title: str | None = Field(default=None, alias="positionTitle")
    job_url: str = Field(alias="jobUrl")
    location: str | None = None
    work_mode: WorkMode | None = Field(default=None, alias="workMode")
    stage: Literal["initial"] = "initial"
    notes: str | None = None
    interviews: list[DraftInterview] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)

    model_config = ConfigDict(populate_by_name=True)


class ServiceErrorResponse(BaseModel):
    code: Literal[
        "INVALID_JOB_URL",
        "JOB_FETCH_TIMEOUT",
        "JOB_FETCH_FAILED",
        "JOB_PAGE_NOT_READABLE",
        "AI_EXTRACTION_FAILED",
        "AI_SERVICE_INTERNAL_ERROR",
        "MISSING_INTERNAL_API_KEY",
        "INVALID_INTERNAL_API_KEY",
    ]
    message: str


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"

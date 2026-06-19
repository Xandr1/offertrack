from app.models import DraftInterview, DraftResponse, ExtractedDraft

INTERVIEW_KEYWORDS = (
    "interview",
    "screening",
    "phone screen",
    "recruiter call",
    "hr call",
    "technical round",
    "technical interview",
    "hiring manager",
    "team match",
    "behavioral",
    "take-home",
    "take home",
    "home assignment",
    "assessment",
)


def build_draft_response(job_url: str, extracted: ExtractedDraft, page_text: str) -> DraftResponse:
  warnings = list(extracted.warnings)
  interviews: list[DraftInterview] = []

  if _has_explicit_interview_mentions(page_text):
    interviews = [DraftInterview(type=interview.type) for interview in extracted.interviews]
  elif extracted.interviews:
    warnings.append("Interview rounds were omitted because the job page did not mention them.")

  if extracted.company_name is None:
    warnings.append("Company name could not be determined.")

  if extracted.position_title is None:
    warnings.append("Position title could not be determined.")

  if extracted.location is None:
    warnings.append("Location could not be determined.")

  if extracted.work_mode is None:
    warnings.append("Work mode could not be determined.")

  if extracted.notes is None:
    warnings.append("Vacancy summary could not be determined.")

  return DraftResponse(
      companyName=extracted.company_name,
      positionTitle=extracted.position_title,
      jobUrl=job_url,
      location=extracted.location,
      workMode=extracted.work_mode,
      notes=extracted.notes,
      interviews=interviews,
      warnings=_dedupe(warnings),
  )


def _has_explicit_interview_mentions(page_text: str) -> bool:
  lowered_text = page_text.lower()
  return any(keyword in lowered_text for keyword in INTERVIEW_KEYWORDS)


def _dedupe(values: list[str]) -> list[str]:
  seen: set[str] = set()
  result: list[str] = []

  for value in values:
    cleaned_value = value.strip()

    if not cleaned_value or cleaned_value in seen:
      continue

    seen.add(cleaned_value)
    result.append(cleaned_value)

  return result

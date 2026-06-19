from fastapi.testclient import TestClient

from app.fetcher import FetchResult
from app.main import create_app
from app.models import ExtractedDraft, ExtractedInterview
from app.settings import Settings


class FakeFetcher:
  def __init__(self, body: bytes) -> None:
    self.body = body

  async def fetch(self, job_url: str) -> FetchResult:
    return FetchResult(url=job_url, body=self.body, content_type="text/html")


class FakeExtractor:
  def __init__(self, extracted: ExtractedDraft) -> None:
    self.extracted = extracted

  def extract(self, job_text: str) -> ExtractedDraft:
    return self.extracted


class InvalidExtractor:
  def extract(self, job_text: str) -> ExtractedDraft:
    return ExtractedDraft.model_validate(
        {
            "company_name": "Acme",
            "position_title": "Backend Engineer",
            "work_mode": "space",
            "notes": "Invalid work mode should fail validation.",
        }
    )


def test_maps_mocked_openai_structured_output_to_draft_response() -> None:
  client = _client(
      b"<html><body>Acme Backend Engineer remote role.</body></html>",
      FakeExtractor(
          ExtractedDraft(
              company_name="Acme",
              position_title="Backend Engineer",
              location="Remote",
              work_mode="remote",
              notes="Acme is hiring a backend engineer for platform APIs. The role is remote.",
              warnings=[],
          )
      ),
  )

  response = client.post("/parse-job", json={"jobUrl": "https://example.com/jobs/1"})

  assert response.status_code == 200
  assert response.json() == {
      "companyName": "Acme",
      "positionTitle": "Backend Engineer",
      "jobUrl": "https://example.com/jobs/1",
      "location": "Remote",
      "workMode": "remote",
      "stage": "initial",
      "notes": "Acme is hiring a backend engineer for platform APIs. The role is remote.",
      "interviews": [],
      "warnings": [],
  }


def test_handles_invalid_model_output_safely() -> None:
  client = _client(b"<html><body>Acme Backend Engineer.</body></html>", InvalidExtractor())

  response = client.post("/parse-job", json={"jobUrl": "https://example.com/jobs/1"})

  assert response.status_code == 502
  assert response.json()["detail"] == "Could not extract application draft."


def test_does_not_invent_interview_rounds_when_none_are_present() -> None:
  client = _client(
      b"<html><body>Acme Backend Engineer remote role.</body></html>",
      FakeExtractor(
          ExtractedDraft(
              company_name="Acme",
              position_title="Backend Engineer",
              location="Remote",
              work_mode="remote",
              notes="Acme is hiring a backend engineer for platform APIs. The role is remote.",
              interviews=[ExtractedInterview(type="technical")],
              warnings=[],
          )
      ),
  )

  response = client.post("/parse-job", json={"jobUrl": "https://example.com/jobs/1"})

  assert response.status_code == 200
  assert response.json()["interviews"] == []
  assert "Interview rounds were omitted" in response.json()["warnings"][0]


def test_parses_explicit_interview_rounds_as_planned_interviews() -> None:
  client = _client(
      b"<html><body>The process includes a recruiter call and technical interview.</body></html>",
      FakeExtractor(
          ExtractedDraft(
              company_name="Acme",
              position_title="Backend Engineer",
              location="Remote",
              work_mode="remote",
              notes="Acme is hiring a backend engineer for platform APIs. The role is remote.",
              interviews=[
                  ExtractedInterview(type="recruiter"),
                  ExtractedInterview(type="technical"),
              ],
              warnings=[],
          )
      ),
  )

  response = client.post("/parse-job", json={"jobUrl": "https://example.com/jobs/1"})

  assert response.status_code == 200
  assert response.json()["interviews"] == [
      {"type": "recruiter", "status": "planned", "scheduledAt": None},
      {"type": "technical", "status": "planned", "scheduledAt": None},
  ]


def _client(body: bytes, extractor) -> TestClient:
  settings = Settings(openai_api_key="", openai_model="test-model")
  app = create_app(
      settings=settings,
      fetcher_factory=lambda resolved_settings: FakeFetcher(body),
      extractor_factory=lambda resolved_settings: extractor,
  )

  return TestClient(app)

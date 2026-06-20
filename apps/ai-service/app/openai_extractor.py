from openai import APITimeoutError, OpenAI
from pydantic import ValidationError

from app.models import ExtractedDraft
from app.settings import Settings

SYSTEM_PROMPT = """
You extract application draft fields from job vacancy text.
Use only facts present in the job page text.
Return null for fields that are missing or uncertain.
The notes field must be a concise 2-3 sentence vacancy summary.
Only include interview rounds if the job page explicitly mentions them.
Use interview types from this set only: hr, recruiter, technical, hiring_manager,
team_match, home_assignment, behavioral, other.
Use other only for an explicitly mentioned round that cannot be classified.
Add warnings for missing fields, uncertainty, blocked content, or weak evidence.
"""


class ExtractionError(RuntimeError):
    pass


class MissingOpenAiApiKeyError(ExtractionError):
    pass


class OpenAiTimeoutError(ExtractionError):
    pass


class OpenAiDraftExtractor:
    def __init__(self, settings: Settings, client: OpenAI | None = None) -> None:
        self.settings = settings
        self._client = client

    def extract(self, job_text: str) -> ExtractedDraft:
        if not self.settings.openai_api_key and self._client is None:
            raise MissingOpenAiApiKeyError("OPENAI_API_KEY is not configured.")

        try:
            response = self.client.responses.parse(
                model=self.settings.openai_model,
                input=[
                    {"role": "system", "content": SYSTEM_PROMPT.strip()},
                    {"role": "user", "content": job_text},
                ],
                text_format=ExtractedDraft,
            )
            parsed = response.output_parsed

            if parsed is None:
                raise ExtractionError("OpenAI returned no parsed output.")

            if isinstance(parsed, ExtractedDraft):
                return parsed

            return ExtractedDraft.model_validate(parsed)
        except APITimeoutError as exception:
            raise OpenAiTimeoutError("OpenAI extraction timed out.") from exception
        except ExtractionError:
            raise
        except ValidationError as exception:
            raise ExtractionError("OpenAI output did not match the draft schema.") from exception
        except Exception as exception:
            raise ExtractionError("OpenAI extraction failed.") from exception

    @property
    def client(self) -> OpenAI:
        if self._client is None:
            self._client = OpenAI(
                api_key=self.settings.openai_api_key,
                timeout=self.settings.openai_timeout_seconds,
            )

        return self._client

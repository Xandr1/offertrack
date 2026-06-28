from openai import APITimeoutError, OpenAI
from pydantic import ValidationError

from app.models import ExtractedDraft
from app.settings import Settings

SYSTEM_PROMPT = """
You extract application draft fields from job vacancy text.

Use only information present in the provided cleaned job vacancy text. Do not use
outside knowledge, employer reputation, the URL, page host, or assumptions.
Return null for any scalar field that is missing, ambiguous, or weakly supported.

Return the existing draft fields only:
company_name, position_title, location, work_mode, notes, interviews, warnings.

Warnings:
- Add warnings for missing fields, ambiguity, blocked or partial page content,
  weak evidence, and omitted or incomplete interview process details.
- Keep warnings concise and specific.

Company name:
- Extract the hiring company or employer name.
- Do not use job board names, page hosts, website names, platforms, recruiters,
  agencies, partners, or parent companies unless the text clearly identifies them
  as the employer.
- If multiple company names are present, prefer the company offering the role.

Position title:
- Prefer the job vacancy title or header.
- Preserve seniority and level when present, such as Senior, Staff, Principal,
  Lead, L5, or IC4.
- Preserve specialization, platform, or domain when present, such as Android,
  Backend, Frontend, Full Stack, MLOps, Data, or Platform.
- If the title is generic but the stack is clearly central to the role, include
  the stack in parentheses, such as Senior Software Engineer (Android), Backend
  Engineer (Java), or Software Developer (.NET).
- Do not add technologies unless they are clearly central to the role.
- Do not build a long title from every mentioned technology.

Location:
- Prefer the explicit job location from the vacancy text.
- Return location as a concise string using only city and country information
explicitly present in the vacancy text.
- If one city and one country are clearly stated, use: City, Country.
- If multiple cities in the same country are clearly stated, use:
City 1, City 2, Country.
- If multiple countries are clearly stated and city names are not clear or not
mentioned, use: Country 1, Country 2.
- If multiple city-country pairs are clearly stated, preserve them in a concise
format, such as: City 1, Country 1; City 2, Country 2.
- If no city is listed but one or more countries are clearly stated for the
vacancy, return only the country or countries.
- If the role is remote and no country can be determined from the text, return
null.
- If neither city nor country is clearly specified, return null.
- Do not infer location from the URL, page host, company headquarters, or outside
knowledge.

Work mode:
- Use only remote, hybrid, onsite, or null.
- Return remote only when the text explicitly says the role is remote or clearly
  supports remote work.
- Return hybrid only when the text explicitly mentions hybrid work or regular
  office attendance combined with remote work.
- Return onsite only when the text clearly says office-based, on-site, or
  requires regular work from a specific office.
- Return null if work mode is not clear.

Notes:
- If enough role information is present, write exactly 2 concise sentences.
- If the page does not contain enough role-specific information, return null.
- Summarize role scope, core responsibilities, notable technologies or domain,
  and salary when it is clearly stated.
- Do not include generic company marketing, benefits, equal opportunity text,
  legal disclaimers, or application instructions.
- Do not invent missing responsibilities, technologies, or salary.

Interviews:
- Include only explicitly mentioned hiring or interview stages.
- Do not infer interview rounds from standard hiring practices.
- Use interview types from this set only: hr, recruiter, technical,
  hiring_manager, team_match, home_assignment, behavioral, other.
- Use other only for an explicitly mentioned stage that cannot be classified.
- If interview stages are not explicitly mentioned, return an empty list.
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

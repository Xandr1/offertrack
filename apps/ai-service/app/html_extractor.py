import re

from bs4 import BeautifulSoup

from app.settings import DEFAULT_MAX_JOB_TEXT_CHARS


def extract_readable_text(
    body: bytes,
    content_type: str | None = None,
    *,
    max_chars: int = DEFAULT_MAX_JOB_TEXT_CHARS,
) -> str:
    if not body:
        return ""

    soup = BeautifulSoup(body, "html.parser")

    for element in soup(["script", "style", "noscript", "template", "svg"]):
        element.decompose()

    text = soup.get_text(" ", strip=True)
    text = re.sub(r"\s+", " ", text).strip()

    return text[:max_chars]

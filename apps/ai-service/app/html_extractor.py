import re

from bs4 import BeautifulSoup

MAX_JOB_TEXT_CHARS = 20_000


def extract_readable_text(body: bytes, content_type: str | None = None) -> str:
    if not body:
        return ""

    soup = BeautifulSoup(body, "html.parser")

    for element in soup(["script", "style", "noscript", "template", "svg"]):
        element.decompose()

    text = soup.get_text(" ", strip=True)
    text = re.sub(r"\s+", " ", text).strip()

    return text[:MAX_JOB_TEXT_CHARS]

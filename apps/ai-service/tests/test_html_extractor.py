from app.html_extractor import extract_readable_text
from app.settings import DEFAULT_MAX_JOB_TEXT_CHARS


def test_extracts_simple_html_content() -> None:
    html = b"""
  <html>
    <head><style>.hidden{display:none}</style><script>ignore()</script></head>
    <body>
      <h1>Backend Engineer</h1>
      <p>Acme builds developer tools.</p>
    </body>
  </html>
  """

    text = extract_readable_text(html, "text/html")

    assert "Backend Engineer" in text
    assert "Acme builds developer tools." in text
    assert "ignore()" not in text


def test_truncates_clean_text_to_default_limit() -> None:
    visible_text = "x" * (DEFAULT_MAX_JOB_TEXT_CHARS + 1)

    text = extract_readable_text(f"<p>{visible_text}</p>".encode())

    assert text == "x" * DEFAULT_MAX_JOB_TEXT_CHARS


def test_truncates_clean_text_to_custom_limit() -> None:
    text = extract_readable_text(b"<p>Backend Engineer responsibilities</p>", max_chars=7)

    assert text == "Backend"


def test_truncates_after_removing_non_readable_html() -> None:
    html = b"<script>" + (b"ignored" * 100) + b"</script><p>Visible responsibilities</p>"

    text = extract_readable_text(html, max_chars=7)

    assert text == "Visible"

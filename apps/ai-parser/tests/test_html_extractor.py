from app.html_extractor import extract_readable_text


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

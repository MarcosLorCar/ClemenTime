# Schedule PDF to Structured JSON Converter (`parse_schedule.py`)

Automated tool to convert course schedule PDFs into structured JSON schedule files (`output_schedule.json`). Uses **Google Gemini (LLM)** for visual table extraction and an **interactive CLI** for subject/professor name mapping.

---

## Architecture Overview

```
               +-----------------------------+
               |     Input Schedule PDF      |
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               | pdf2image (Page Conversion) |
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               |    Gemini-2.0-Flash API     | <---> mappings.json (Context)
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               |  Interactive CLI Mapper     | <---> mappings.json (Update)
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               |   Output Schedule JSON      |
               +-----------------------------+
```

---

## Installation & Dependencies

This project uses `uv` for dependency management.

```bash
uv sync
```

> [!IMPORTANT]
> You must also have `poppler` installed on your system for `pdf2image` to work.

---

## Setup

1.  **API Key**: Obtain a Gemini API key from [Google AI Studio](https://aistudio.google.com/).
2.  **Configuration**: You can set your API key and preferred model in two ways:
    - **Option A: .env file (Recommended)**: Create a file named `.env` in the same directory as the script:
      ```text
      GEMINI_API_KEY=your_api_key_here
      GEMINI_MODEL=gemini-2.0-flash
      ```
    - **Option B: Environment Variable**:
      ```bash
      export GEMINI_API_KEY="your_api_key_here"
      ```

---

## Usage Instructions

### 1. Run the script

By default, the script looks for `horarios.pdf` in the current directory:

```bash
uv run parse_schedule.py
```

Or specify a custom file:

```bash
uv run parse_schedule.py my_schedule.pdf
```

### 2. Interactive Mapping

If Gemini finds an abbreviation or code (subject, professor, or classroom) that is not in `mappings.json`, the script will pause and prompt you:

```
[?] Unknown matter found: 'TeCo'
    Enter full name for 'TeCo' (or press Enter to use as is): Tecnologia de Computadores
```

Your answers are automatically saved to `mappings.json` and used in future runs (including being sent to Gemini as context).

### 3. Rate Limiting Handling

The script includes exponential backoff retry logic. If the API returns a rate limit error (`429`), it will wait and retry automatically until the page is successfully processed.

---

## Output Configuration

- **Input**: PDF files (converted to 200 DPI images).
- **Mappings**: `mappings.json` (stores persistent name resolutions).
- **Output**: `output_schedule.json` (structured JSON for the app).

# Schedule PDF to Structured JSON Converter (`convert_schedule.py`)

Automated tool to convert course schedule PDFs into structured JSON schedule files (`primer_cuatrimestre.json`). Uses **Docling** for PDF-to-Markdown table extraction, interactive CLI prompting for subject/professor name mapping, and advanced slot deduplication and merging algorithms.

---

## Architecture Overview

```
               +-----------------------------+
               |     Input Schedule PDF      |
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               | Docling Document Converter  |
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               | Intermediate Markdown (.md) |  <-- Cached automatically
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               |   Markdown Table Parser     |
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               |  Interactive CLI Mapper     | <---> mappings.json (Cache)
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               | Slot Merger & Deduplicator  |
               +--------------+--------------+
                              |
                              v
               +-----------------------------+
               |   Output Schedule JSON      |
               +-----------------------------+
```

---

## Installation & Dependencies

Requires Python 3.9+ and `docling`.

```bash
pip install docling
```

---

## CLI Usage Instructions

### 1. Default Interactive Mode (Recommended)

Converts the PDF schedule into Markdown, prompts for any unmapped subject codes or professor abbreviations, saves confirmed mappings to `mappings.json`, and exports the final JSON file.

```bash
python3 convert_schedule.py ESI2026-27-GRADO_1C_Grupos.pdf -o primer_cuatrimestre.json
```

### 2. Fast Run Using Cached Markdown

If the intermediate `<pdf_name>.md` file already exists, the script skips the Docling PDF rendering step to save time:

```bash
python3 convert_schedule.py ESI2026-27-GRADO_1C_Grupos.pdf --md-file ESI2026-27-GRADO_1C_Grupos.md -o primer_cuatrimestre.json
```

### 3. Force PDF Re-conversion

Bypasses any existing Markdown cache and forces Docling to re-parse the PDF:

```bash
python3 convert_schedule.py ESI2026-27-GRADO_1C_Grupos.pdf --force-pdf -o primer_cuatrimestre.json
```

### 4. Non-Interactive / Batch Mode

Runs without terminal prompts (uses raw codes/abbreviations if unmapped):

```bash
python3 convert_schedule.py ESI2026-27-GRADO_1C_Grupos.pdf -o output.json --non-interactive
```

---

## Mapping Configuration (`mappings.json`)

The script persists confirmed mappings in `mappings.json`:

```json
{
  "matters": {
    "FunProg1": "Fundamentos de la Programación 1",
    "TeCo": "Tecnología de Computadores",
    "Calculo": "Cálculo",
    "Fisica": "Física",
    "FunGesEmpr": "Fundamentos de Gestión Empresarial"
  },
  "professors": {
    "Jesus.Serrano": "Jesús Serrano",
    "MariaL.Lopez": "María L. López",
    "Antonio.Adan": "Antonio Adán",
    "PeterS.Normile": "Peter S. Normile",
    "Javier.Verdugo": "Javier Verdugo"
  },
  "classrooms": {
    "0.04-Hedy": "0.04-Hedy Lamarr",
    "0.05-Eds": "0.05-Edsger W.",
    "LD2-Den": "LD2-Dennis Ritchie"
  }
}
```

---

## CLI Command Arguments

| Argument | Long Option | Default | Description |
|---|---|---|---|
| `pdf_path` | — | *(Required)* | Path to input PDF file |
| `-o` | `--output` | `output_schedule.json` | Path for exported schedule JSON file |
| — | `--md-file` | `<pdf_basename>.md` | Path to intermediate Markdown cache file |
| — | `--mappings-file` | `mappings.json` | Path to persistent symbol mappings JSON |
| — | `--non-interactive` | `False` | Run headless without terminal prompts |
| — | `--force-pdf` | `False` | Force re-running Docling even if `.md` cache exists |

---

## Key Technical Details

- **Horizontal Deduplication**: Deduplicates multi-column PDF table spans (e.g. repeated `Martes | Martes` headers).
- **Vertical Time Merging**: Automatically merges contiguous 1.5-hour time rows (`08:30–10:00` + `10:00–11:30`) for matching subjects into continuous 3-hour slots (`08:30–11:30`).
- **Continuation Cell Recovery**: Resolves split PDF table cells where row 1 contains the subject code and row 2 contains room/professor details.

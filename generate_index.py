import json
import os
from datetime import datetime

def generate_index(schedules_dir="./schedules"):
    index_entries = []

    for filename in sorted(os.listdir(schedules_dir)):
        if filename.endswith(".json") and filename != "schedules_index.json":
            file_path = os.path.join(schedules_dir, filename)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    data = json.load(f)

                schedule_id = os.path.splitext(filename)[0]
                title = data.get("title", filename)

                index_entries.append({
                    "id": schedule_id,
                    "title": title,
                    "description": "Horario oficial",
                    "path": filename,
                    "updatedTime": datetime.now().strftime("%Y-%m-%d")
                })
            except Exception as e:
                print(f"Skipping {filename}: {e}")

    output_path = os.path.join(schedules_dir, "schedules_index.json")

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(index_entries, f, indent=2, ensure_ascii=False)

    print(f"Successfully generated {output_path} with {len(index_entries)} schedules.")

if __name__ == "__main__":
    generate_index()
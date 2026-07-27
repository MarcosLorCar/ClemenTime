import os
from google import genai
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

def list_models():
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("Error: GEMINI_API_KEY not found in .env or environment.")
        return

    client = genai.Client(api_key=api_key)
    print("Available models for your API key:")
    try:
        # Use the models.list() method which is the standard in the google-genai SDK
        for model in client.models.list():
            print(f"- {model.name}")
    except Exception as e:
        print(f"Error listing models: {e}")

if __name__ == "__main__":
    list_models()

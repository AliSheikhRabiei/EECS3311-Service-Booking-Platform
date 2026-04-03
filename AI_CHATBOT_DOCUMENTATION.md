# AI Customer Assistant – Documentation

## What it does

The AI Customer Assistant is a chatbot accessible from the Client dashboard.
Clients can ask questions about the platform and receive helpful answers in real time.

## Example questions it can answer

| Question | Example answer |
|---|---|
| "How do I book a session?" | Explains the 6-step booking flow |
| "What payment methods do you accept?" | Lists credit card, debit, PayPal, bank transfer |
| "Can I cancel my booking?" | Explains cancellation policy and refund rules |
| "What services are available?" | Lists currently offered services with prices |
| "What happens after I pay?" | Explains PAID → session → COMPLETED flow |
| "What does REQUESTED status mean?" | Explains the booking lifecycle |

## Architecture

```
Client Browser
     │
     │  POST /chat  { "message": "How do I book?" }
     ▼
Backend (ChatHandler.java)
     │
     │  Builds system prompt with public platform info
     │  Calls OpenAI API  (key stored in env variable)
     │
     ▼
OpenAI gpt-3.5-turbo
     │
     ▼
Backend returns { "reply": "Here is how to book..." }
     │
     ▼
Client Browser renders the reply in the chat panel
```

## Privacy and safety measures

The following rules are enforced in `ChatHandler.java`:

1. **No personal data sent to AI** — The system prompt contains only public platform
   information: how to book, payment methods, cancellation policy, booking statuses,
   and a list of service titles and prices. No user names, booking IDs, or payment
   details are ever included.

2. **No database access from AI** — The AI receives a pre-built text prompt. It has
   no connection to the database and cannot query or modify any records.

3. **Authentication required** — The `/chat` endpoint requires a valid Bearer token.
   Unauthenticated requests receive a 401 response.

4. **API key in environment variable** — The OpenAI API key is read from
   `OPENAI_API_KEY` environment variable, never hardcoded in source code.

5. **XSS protection** — All AI responses are HTML-escaped before being rendered
   in the browser, preventing script injection.

6. **AI is advisory only** — The system prompt explicitly instructs the model not
   to perform actions or claim access to user accounts. Responses are informational only.

## System context provided to the AI

The prompt sent to OpenAI includes:
- Platform description
- Step-by-step booking process
- Accepted payment methods and validation rules
- Cancellation and refund policy
- Booking status definitions
- Currently available services (titles and prices only)

## API integration

- **Provider:** OpenAI
- **Model:** `gpt-3.5-turbo`
- **Endpoint:** `POST https://api.openai.com/v1/chat/completions`
- **Max tokens:** 300 per response
- **HTTP client:** Java built-in `java.net.http.HttpClient` (no extra dependency)

## How to configure the API key

1. Get your key from https://platform.openai.com/api-keys
2. Create a `.env` file in the project root (copy from `.env.example`):
   ```
   OPENAI_API_KEY=sk-your-real-key-here
   ```
3. Run `docker-compose up --build` — Docker reads the `.env` file automatically
4. The `.env` file is listed in `.gitignore` and must never be committed to GitHub

## How to access the chatbot

1. Log in as a **Client** at `http://localhost:3000`
2. Look for the 💬 button in the bottom-right corner of the screen
3. Click it to open the chat panel
4. Type any question about the platform and press Enter or click Send

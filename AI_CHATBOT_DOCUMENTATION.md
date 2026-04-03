# AI Customer Assistant – Documentation

## What it does

The AI Customer Assistant is a chatbot accessible from the Client dashboard (💬 button, bottom-right corner). Clients type questions about the platform and receive real-time helpful answers. The AI has no access to user data or the database — it only knows public platform information.

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
     │  Calls hpc-ai.com API (key stored in OPENAI_API_KEY env var)
     │
     ▼
hpc-ai.com — minimax/minimax-m2.5 model
     │
     ▼
Backend returns { "reply": "Here is how to book..." }
     │
     ▼
Client Browser renders the reply in the chat panel
```

## API Integration Details

| Property | Value |
|---|---|
| **Provider** | [hpc-ai.com](https://www.hpc-ai.com) |
| **API Endpoint** | `https://api.hpc-ai.com/inference/v1/chat/completions` |
| **Model** | `minimax/minimax-m2.5` |
| **Protocol** | OpenAI-compatible chat completions format |
| **Max tokens per response** | 300 |
| **HTTP client** | Java built-in `java.net.http.HttpClient` (Java 11+, no extra dependency) |

The hpc-ai.com API uses the same request/response JSON format as OpenAI's `/v1/chat/completions` endpoint — the same `messages[]`, `model`, `max_tokens` request fields and the same `choices[0].message.content` response path. This is why the environment variable is named `OPENAI_API_KEY` (the variable name is generic; the value is a hpc-ai.com key).

## Privacy and safety measures

The following rules are enforced in `ChatHandler.java`:

1. **No personal data sent to AI** — The system prompt contains only public platform information: how to book, payment methods, cancellation policy, booking status definitions, and a list of service titles and prices. No user names, booking IDs, email addresses, or payment details are ever included.

2. **No database access from AI** — The AI receives a pre-built text prompt. It has no connection to the database and cannot query or modify any records.

3. **Authentication required** — The `/chat` endpoint requires a valid Bearer token. Unauthenticated requests receive a 401 response. The chatbot is only accessible to logged-in clients.

4. **API key in environment variable** — The hpc-ai.com API key is read from the `OPENAI_API_KEY` environment variable (set in the `.env` file and injected by Docker Compose). It is never in source code.

5. **XSS protection** — All AI responses are HTML-escaped in `escapeHtml()` before being rendered in the browser, preventing script injection from AI output.

6. **AI is advisory only** — The system prompt explicitly instructs the model not to perform actions, not to make up user-specific information, and not to claim access to user accounts. Responses are informational only.

## System context provided to the AI

The prompt sent to hpc-ai.com includes only:
- Platform description (what the platform is)
- Step-by-step booking process
- Accepted payment methods and their validation rules
- Cancellation and refund policy
- Booking status definitions (REQUESTED, CONFIRMED, etc.)
- Currently available services (titles and prices only — from `ServiceCatalog.listAllServices()`)

## How to configure the API key

1. Get your key from your hpc-ai.com dashboard at [https://www.hpc-ai.com](https://www.hpc-ai.com)
2. Create a `.env` file in the project root (copy from `.env.example`):
   ```
   OPENAI_API_KEY=sk-your-real-hpc-ai-key-here
   ```
3. Run `docker-compose up --build` — Docker reads the `.env` file automatically
4. The `.env` file is in `.gitignore` and must **never** be committed to GitHub

## How to access the chatbot

1. Log in as a **Client** at `http://localhost:3000`
2. Look for the 💬 button in the bottom-right corner
3. Click it to open the chat panel
4. Type any question and press Enter or click Send

## Error behaviour

| Condition | Response |
|---|---|
| `OPENAI_API_KEY` not set in environment | 500: "AI service is not configured. Set the OPENAI_API_KEY environment variable." |
| hpc-ai.com returns non-200 | 500: "AI service temporarily unavailable. Please try again." (error logged to backend console) |
| Network timeout (20s) | 500: "AI service temporarily unavailable." |
| No Bearer token in request | 401: "Unauthorised. Please log in." |
| Empty message body | 400: "message field is required." |

## Relevant source file

`Backend/src/main/java/com/platform/http/handler/ChatHandler.java`

# Intelli API

A Java Spring Boot REST API that provides intelligent search via Claude API and caches results in a vector database (Qdrant).

## Features
- **POST /intelli/search**
- Supports two modes: `tech` (explanation, use cases, code) and `language` (definition, example sentences)
- Checks vector database for similar queries before calling Claude API
- Saves new results and embeddings to vector DB

## Requirements
- Java 17+
- Maven
- Qdrant running locally (default: http://localhost:6333)
- Claude API Key

## Setup
1. Clone the repo and enter directory:
    ```sh
    git clone <repo-url>
    cd intelli-api
    ```
2. Set your Claude API key in `src/main/resources/application.properties`:
    ```properties
    claude.api.key=YOUR_CLAUDE_API_KEY
    ```
3. Start Qdrant locally (see https://qdrant.tech/documentation/quick-start/)
4. Build and run the API:
    ```sh
    mvn spring-boot:run
    ```

## Example Request
```
POST /intelli/search
Content-Type: application/json
{
  "inputText": "What is a REST API?",
  "mode": "tech"
}
```

## Example Response (mode=tech)
```
{
  "explanation": "A REST API is ...",
  "useCases": ["Integrating services", "Mobile backend"],
  "code": ["curl ...", "fetch(...) ..."]
}
```

## Example Response (mode=language)
```
{
  "definition": "A REST API is ...",
  "exampleSentences": ["I built a REST API.", "REST APIs are popular."]
}
```

## Notes
- Embedding and Claude API integration are stubbed for demo; implement with actual API calls as needed.
- Update vector DB logic in `VectorDbClient` for production usage.

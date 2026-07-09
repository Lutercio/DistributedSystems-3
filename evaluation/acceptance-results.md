# UFRN Responde acceptance results

Date: 2026-07-07

## Infrastructure

- Maven reactor verification: passed for all eight modules.
- Docker Compose rendering: valid.
- Clean bounded-memory rebuild: passed.
- All twenty normal-profile services reached their expected running or healthy state.
- Live representative containers use the configured memory ceilings and bounded restart policies.

## RAG ingestion gate

Status: passed after migration to Groq chat and local Ollama embeddings.

The implementation now uses `bge-m3:567m` through the internal Ollama service,
so ingestion no longer depends on an external paid embedding API. Ingestion
completed with 341 vectors, all with 1024 dimensions, and one recorded corpus
version. A live request through the Gateway returned a grounded Groq answer
using articles 224, 225, and 226.

## Remaining evaluation

Baseline, capacity, and resilience JMeter campaigns remain separate evaluation
activities. The functional RAG gate is no longer blocked.

# Versioned regulation corpus

`regulamento_dos_cursos_de_graduacao.pdf` is the official source used by both
the RAG ingestion process and the read-only regulation MCP server. The
`metadata.properties` checksum is verified before parsing or ingestion.

To ingest the current release after starting PostgreSQL, run the `rag-service`
image once with `APP_INGESTION_ENABLED=true`. Normal service instances keep
this flag disabled.

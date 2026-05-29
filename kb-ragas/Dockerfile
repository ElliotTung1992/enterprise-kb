FROM python:3.11-slim

WORKDIR /app
ENV PIP_DEFAULT_TIMEOUT=120

COPY kb-ragas/pyproject.toml /app/pyproject.toml
COPY kb-ragas/kb_ragas /app/kb_ragas
RUN pip install --retries 5 --no-cache-dir .

EXPOSE 8000

CMD ["uvicorn", "kb_ragas.app:app", "--host", "0.0.0.0", "--port", "8000"]

from __future__ import annotations

import os
import threading
import uuid
from typing import Any

from fastapi import BackgroundTasks, FastAPI, HTTPException
from pydantic import BaseModel, Field


class RagasItem(BaseModel):
    caseId: uuid.UUID
    question: str
    answer: str
    contexts: list[str]
    groundTruth: str | None = None


class EvaluationRequest(BaseModel):
    jobId: uuid.UUID | None = None
    items: list[RagasItem]
    judgeProvider: str = "DASHSCOPE"
    judgeModel: str = "qwen-max"
    embeddingProvider: str = "DASHSCOPE"
    embeddingModel: str = "text-embedding-v3"
    metrics: list[str] = Field(default_factory=lambda: [
        "faithfulness",
        "answer_relevancy",
        "context_precision",
        "context_recall",
    ])


class Progress(BaseModel):
    done: int
    total: int


class RagasResult(BaseModel):
    caseId: uuid.UUID
    scores: dict[str, float]
    breakdown: dict[str, Any] = Field(default_factory=dict)


class JobState(BaseModel):
    jobId: uuid.UUID
    status: str
    progress: Progress
    results: list[RagasResult] = Field(default_factory=list)
    summary: dict[str, float] = Field(default_factory=dict)
    error: str | None = None


app = FastAPI(title="Enterprise KB Ragas Service")
jobs: dict[uuid.UUID, JobState] = {}
jobs_lock = threading.Lock()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "ragasVersion": _ragas_version(),
        "mockMode": _mock_mode(),
        "supportedProviders": ["DASHSCOPE", "OPENAI"],
    }


@app.post("/evaluations")
def create_evaluation(req: EvaluationRequest, background_tasks: BackgroundTasks) -> dict[str, Any]:
    if not req.items:
        raise HTTPException(status_code=400, detail="items must not be empty")
    if "context_recall" in req.metrics and any(not item.groundTruth for item in req.items):
        raise HTTPException(status_code=400, detail="context_recall requires groundTruth for every item")

    job_id = req.jobId or uuid.uuid4()
    state = JobState(jobId=job_id, status="PENDING", progress=Progress(done=0, total=len(req.items)))
    with jobs_lock:
        jobs[job_id] = state
    background_tasks.add_task(_run_job, job_id, req)
    return {"jobId": job_id, "status": "PENDING", "itemCount": len(req.items)}


@app.get("/evaluations/{job_id}")
def get_evaluation(job_id: uuid.UUID) -> JobState:
    with jobs_lock:
        state = jobs.get(job_id)
    if state is None:
        raise HTTPException(status_code=404, detail="job not found")
    return state


def _run_job(job_id: uuid.UUID, req: EvaluationRequest) -> None:
    _update(job_id, status="RUNNING")
    try:
        results = _mock_evaluate(req) if _mock_mode() else _ragas_evaluate(req)
        summary = _summary(results)
        _update(job_id, status="SUCCEEDED", results=results, summary=summary,
                progress=Progress(done=len(req.items), total=len(req.items)))
    except Exception as exc:  # noqa: BLE001 - returned to Java as job failure
        _update(job_id, status="FAILED", error=str(exc))


def _update(job_id: uuid.UUID, **changes: Any) -> None:
    with jobs_lock:
        state = jobs[job_id]
        data = state.model_dump()
        data.update(changes)
        jobs[job_id] = JobState(**data)


def _mock_evaluate(req: EvaluationRequest) -> list[RagasResult]:
    results: list[RagasResult] = []
    for item in req.items:
        scores = {metric: 1.0 for metric in req.metrics}
        results.append(RagasResult(caseId=item.caseId, scores=scores, breakdown={"mock": True}))
    return results


def _ragas_evaluate(req: EvaluationRequest) -> list[RagasResult]:
    from datasets import Dataset
    from langchain_openai import ChatOpenAI, OpenAIEmbeddings
    from ragas import evaluate
    from ragas.embeddings import LangchainEmbeddingsWrapper
    from ragas.llms import LangchainLLMWrapper
    from ragas.metrics import answer_relevancy, context_precision, context_recall, faithfulness

    metric_map = {
        "faithfulness": faithfulness,
        "answer_relevancy": answer_relevancy,
        "context_precision": context_precision,
        "context_recall": context_recall,
    }
    selected_metrics = [metric_map[name] for name in req.metrics if name in metric_map]
    if not selected_metrics:
        raise ValueError("no supported metrics requested")

    api_key, base_url = _provider_env(req.judgeProvider)
    llm = LangchainLLMWrapper(ChatOpenAI(
        api_key=api_key,
        base_url=base_url,
        model=req.judgeModel,
        temperature=0,
    ))
    embeddings = LangchainEmbeddingsWrapper(OpenAIEmbeddings(
        api_key=api_key,
        base_url=base_url,
        model=req.embeddingModel,
    ))

    dataset = Dataset.from_list([
        {
            "question": item.question,
            "answer": item.answer,
            "contexts": item.contexts,
            "ground_truth": item.groundTruth,
        }
        for item in req.items
    ])
    score = evaluate(dataset, metrics=selected_metrics, llm=llm, embeddings=embeddings)
    rows = score.to_pandas().to_dict(orient="records")

    results: list[RagasResult] = []
    for item, row in zip(req.items, rows, strict=True):
        scores: dict[str, float] = {}
        breakdown: dict[str, Any] = {}
        for metric in req.metrics:
            value = row.get(metric)
            if value is None:
                continue
            try:
                scores[metric] = float(value)
            except (TypeError, ValueError):
                breakdown[metric] = value
        results.append(RagasResult(caseId=item.caseId, scores=scores, breakdown=breakdown))
    return results


def _provider_env(provider: str) -> tuple[str, str | None]:
    normalized = provider.upper()
    if normalized == "DASHSCOPE":
        api_key = os.getenv("DASHSCOPE_API_KEY")
        if not api_key:
            raise RuntimeError("DASHSCOPE_API_KEY is not configured")
        return api_key, os.getenv("DASHSCOPE_OPENAI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    if normalized == "OPENAI":
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("OPENAI_API_KEY is not configured")
        return api_key, None
    raise RuntimeError(f"unsupported provider: {provider}")


def _summary(results: list[RagasResult]) -> dict[str, float]:
    totals: dict[str, float] = {}
    counts: dict[str, int] = {}
    for result in results:
        for metric, score in result.scores.items():
            totals[metric] = totals.get(metric, 0.0) + score
            counts[metric] = counts.get(metric, 0) + 1
    return {metric: totals[metric] / counts[metric] for metric in totals}


def _mock_mode() -> bool:
    return os.getenv("KB_RAGAS_MOCK", "false").lower() == "true"


def _ragas_version() -> str:
    try:
        import ragas
        return getattr(ragas, "__version__", "unknown")
    except Exception:  # noqa: BLE001
        return "not-installed"


def main() -> None:
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

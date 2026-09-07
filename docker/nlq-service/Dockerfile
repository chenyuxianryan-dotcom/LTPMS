FROM python:3.11-slim

WORKDIR /app

COPY nlq-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY nlq-service/app ./app
COPY nlq-service/prompts ./prompts
COPY nlq-service/config ./config

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]

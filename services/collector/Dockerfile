FROM python:3.12-alpine
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ app/

EXPOSE 8200

ENV API_URL=http://api:8080

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8200", "--reload"]

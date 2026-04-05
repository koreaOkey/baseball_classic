FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt crawler/
RUN pip install --no-cache-dir -r crawler/requirements.txt

COPY . crawler/

RUN chmod +x crawler/start.sh

CMD ["sh", "crawler/start.sh"]

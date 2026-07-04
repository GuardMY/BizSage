FROM node:20-alpine
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm install

COPY . .

EXPOSE 3000

ENV NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api

CMD ["npx", "next", "dev", "-p", "3000", "-H", "0.0.0.0"]

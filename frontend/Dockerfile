# ---- build stage ----
FROM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- runtime stage: nginx serves the SPA and proxies /api to the backend ----
FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html

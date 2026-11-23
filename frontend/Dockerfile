# ---- build stage ----
FROM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_BACKEND_COUNT=3
ENV VITE_BACKEND_COUNT=$VITE_BACKEND_COUNT
RUN npm run build

# ---- runtime stage: nginx serves the SPA and load-balances /api over the backends ----
FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html

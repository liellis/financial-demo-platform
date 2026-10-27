# Standalone image for the merged Bank Ontology Studio UI + AI-Coding Demo Portal.
# The main project Dockerfile already bundles this frontend into the Python API image;
# use this file only when the UI should be deployed separately behind nginx.
FROM node:22.19.0-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Override when the API service has another DNS name inside the Docker network.
ENV BACKEND_UPSTREAM=api:8000
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

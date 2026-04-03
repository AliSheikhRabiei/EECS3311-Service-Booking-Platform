# Simple nginx container to serve the static HTML/CSS/JS frontend.
# No build step needed — just copy files and serve.
FROM nginx:alpine

# Copy all frontend files into the nginx web root
COPY . /usr/share/nginx/html

# nginx listens on port 80 inside the container.
# docker-compose maps this to port 3000 on your machine.
EXPOSE 80

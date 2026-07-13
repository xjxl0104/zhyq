# ---- 构建阶段:Node 打静态包 ----
FROM node:20-alpine AS build
WORKDIR /build
RUN npm i -g pnpm@9
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install
COPY . .
RUN pnpm build

# ---- 运行阶段:Nginx 托管 + 反代 /api ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

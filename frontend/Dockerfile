# ---- 构建阶段:Node 打静态包 ----
FROM node:20-alpine AS build
WORKDIR /build
RUN npm i -g pnpm@9
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install
COPY . .
ARG VITE_GIT_SHA
RUN node -e 'if (!/^[0-9a-f]{40}$/i.test(process.env.VITE_GIT_SHA || "")) throw new Error("VITE_GIT_SHA must be the full commit used for this build")' \
    && VITE_GIT_SHA="$VITE_GIT_SHA" pnpm build \
    && node -e 'require("node:fs").writeFileSync("dist/version.json", JSON.stringify({commit: process.env.VITE_GIT_SHA.toLowerCase(), built_at: new Date().toISOString()}) + "\n")'

# ---- 运行阶段:Nginx 托管 + 反代 /api ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

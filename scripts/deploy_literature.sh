#!/usr/bin/env bash
# 文献证据库 v1.0 一键部署脚本
# 在阿里云 ECS (8.130.213.44) 上执行
#
# 使用方法：
#   scp scripts/deploy_literature.sh mayl@8.130.213.44:/home/mayl/
#   ssh mayl@8.130.213.44
#   bash /home/mayl/deploy_literature.sh [migrate|ingest|all]
#
# 或者一步完成：
#   ssh mayl@8.130.213.44 'bash -s' < scripts/deploy_literature.sh all

set -euo pipefail

REPO_DIR="/home/mayl/XJie_IOS"
CONTAINER="xjie-api"
ACTION="${1:-all}"   # migrate | ingest | all

step() { echo -e "\n\033[1;36m==>\033[0m $*"; }
ok()   { echo -e "\033[1;32m[ok]\033[0m $*"; }
fail() { echo -e "\033[1;31m[fail]\033[0m $*" >&2; exit 1; }

# 0. 容器存活检查
docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$" \
  || fail "容器 ${CONTAINER} 未运行，请先 docker start ${CONTAINER}"

# 1. 拉最新代码
step "git pull"
cd "${REPO_DIR}"
git fetch origin
LOCAL=$(git rev-parse HEAD)
git pull origin main
NEW=$(git rev-parse HEAD)
if [ "${LOCAL}" = "${NEW}" ]; then
  echo "    代码无更新（${LOCAL:0:7}）"
else
  echo "    ${LOCAL:0:7} -> ${NEW:0:7}"
fi
ok "git pull 完成"

# 2. 重建并重启容器（必要，因为新增了 routers/services/models）
if [ "${ACTION}" = "migrate" ] || [ "${ACTION}" = "all" ]; then
  step "重建后端镜像"
  docker build -t xjie-backend "${REPO_DIR}/backend"
  ok "镜像构建完成"

  step "重启 ${CONTAINER}"
  # 用同样的参数重启：保留环境变量、端口、卷
  ENVFILE=$(docker inspect "${CONTAINER}" --format '{{range .HostConfig.Binds}}{{println .}}{{end}}' | grep -i '\.env' || true)
  PORTS=$(docker inspect "${CONTAINER}" --format '{{range $p, $c := .NetworkSettings.Ports}}{{$p}}={{(index $c 0).HostPort}} {{end}}')
  echo "    当前端口映射: ${PORTS}"
  echo "    .env 挂载: ${ENVFILE:-(无，从镜像读取)}"

  # 不直接 docker rm，避免参数丢失。改用 docker restart
  docker restart "${CONTAINER}"
  ok "${CONTAINER} 重启完成"

  step "等待 backend 健康"
  for i in $(seq 1 30); do
    if docker exec "${CONTAINER}" python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/api/health',timeout=2).status==200 else 1)" >/dev/null 2>&1; then
      ok "backend 已就绪"
      break
    fi
    sleep 1
    [ $i -eq 30 ] && fail "30s 内未就绪，请查看 docker logs ${CONTAINER}"
  done

  # 3. 跑迁移：仅会执行 0010_literature
  step "alembic upgrade head"
  docker exec "${CONTAINER}" alembic upgrade head
  ok "迁移完成（4 张 literature_* 表已创建）"

  # 4. 验证表
  docker exec timescaledb psql -U postgres -d metabodash -c "\dt literature*" 2>/dev/null \
    || echo "    (跳过 psql 验证：DB 用户/库名可能不同)"
fi

# 5. 抓取首期种子（约 500 条 PubMed → Kimi 提取）
if [ "${ACTION}" = "ingest" ] || [ "${ACTION}" = "all" ]; then
  step "抓取种子文献（约 500 条，预计 30-60 分钟）"
  echo "    建议在 tmux 中跑：tmux new -s lit-ingest，然后再执行 bash $0 ingest"
  read -r -p "    继续吗？(y/N) " ans
  [ "${ans,,}" = "y" ] || { echo "    用户取消"; exit 0; }

  docker exec "${CONTAINER}" python -m app.workers.literature_ingest \
    --seed app/workers/literature_seeds.json
  ok "种子抓取完成"

  step "查看入库统计"
  docker exec "${CONTAINER}" python -c "
from app.db.session import SessionLocal
from app.models.literature import Literature, Claim
db = SessionLocal()
print(f'  literature 总数: {db.query(Literature).count()}')
print(f'  claim 总数:      {db.query(Claim).count()}')
print(f'  enabled claim:   {db.query(Claim).filter(Claim.enabled==True).count()}')
"
fi

step "完成"
echo "  - iOS 真机问 \"吃燕麦能降血糖吗\" 验证气泡底部是否出现 [N] 引用"
echo "  - 失败时排查：docker logs ${CONTAINER} --tail 200"

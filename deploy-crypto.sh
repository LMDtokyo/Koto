#!/bin/bash
# Deploys Signal Protocol crypto changes to the server.
# Run from a machine that can SSH to 31.220.43.109.
#
# Usage: bash deploy-crypto.sh

set -e

SERVER="root@31.220.43.109"
KEY="$HOME/.ssh/nova-social"
DEPLOY_DIR="/opt/nova"

echo "=== Deploying Koto Signal Protocol crypto changes ==="

# 1. Copy updated service source files
echo "--- Syncing service code..."
rsync -avz --progress \
  -e "ssh -i $KEY" \
  services/chat/ \
  services/gateway/ \
  services/user/ \
  "$SERVER:$DEPLOY_DIR/services/" \
  --exclude='*.test' \
  --exclude='.git'

# 2. Run new DB migration on the user service postgres
echo "--- Running user service DB migration 002_prekeys..."
ssh -i "$KEY" "$SERVER" "
  export PGPASSWORD=\$(grep POSTGRES_PASSWORD $DEPLOY_DIR/.env | cut -d= -f2)
  psql -U koto -d koto -h 127.0.0.1 -f $DEPLOY_DIR/services/user/migrations/002_prekeys.sql
  echo 'Migration done'
"

# 3. Rebuild Go services
echo "--- Building chat service..."
ssh -i "$KEY" "$SERVER" "
  cd $DEPLOY_DIR/services/chat
  go build -o /usr/local/bin/nova-chat ./cmd/server/
  echo 'chat built'
"

echo "--- Building gateway service..."
ssh -i "$KEY" "$SERVER" "
  cd $DEPLOY_DIR/services/gateway
  go mod tidy
  go build -o /usr/local/bin/nova-gateway ./cmd/server/
  echo 'gateway built'
"

echo "--- Building user service..."
ssh -i "$KEY" "$SERVER" "
  cd $DEPLOY_DIR/services/user
  go build -o /usr/local/bin/nova-user ./cmd/server/
  echo 'user built'
"

# 4. Restart services
echo "--- Restarting services..."
ssh -i "$KEY" "$SERVER" "
  systemctl restart nova-chat nova-gateway nova-user
  sleep 2
  systemctl is-active nova-chat nova-gateway nova-user
"

echo "=== Deployment done. Checking health endpoints... ==="
sleep 3
curl -sf http://31.220.43.109:18080/health || echo "Gateway health check failed"
echo ""
echo "All done."

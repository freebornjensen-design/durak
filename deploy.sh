#!/bin/bash
LOCKFILE=/tmp/durak-deploy.lock
if [ -f "" ]; then
    echo "[DEPLOY] Another deploy is already running. Skipping."
    exit 0
fi
trap "rm -f " EXIT
touch 

cd /var/www/durak
echo "[DEPLOY] Pulling latest code..."
git pull origin master 2>&1
echo "[DEPLOY] Building Java backend..."
cd java && mvn clean package -DskipTests 2>&1 | tail -5
echo "[DEPLOY] Building React frontend..."
cd /var/www/durak/react && npm run build 2>&1 | tail -5
echo "[DEPLOY] Restarting server..."
systemctl restart durak-server 2>&1
echo "[DEPLOY] Done!"

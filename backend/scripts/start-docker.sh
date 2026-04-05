#!/bin/bash

echo "========================================="
echo "电商秒杀系统 - Docker 环境启动脚本"
echo "========================================="

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker 未运行，请先启动 Docker Desktop"
    exit 1
fi

# 创建必要的数据目录
echo "📁 创建数据目录..."
mkdir -p docker/mysql/{data,init}
mkdir -p docker/redis/{data,conf}
mkdir -p docker/nacos/{logs,data}
mkdir -p docker/rocketmq/{namesrv/{logs,store},broker/{logs,store,conf}}

# 停止并删除已有容器（可选）
echo "🧹 清理旧容器..."
docker-compose down -v 2>/dev/null

# 启动服务
echo "🚀 启动 Docker Compose 服务..."
docker-compose up -d

# 等待服务启动
echo "⏳ 等待服务启动（30秒）..."
sleep 30

# 检查服务状态
echo ""
echo "========================================="
echo "📊 服务状态检查"
echo "========================================="

check_service() {
    local name=$1
    local port=$2
    if docker ps --filter "name=$name" --format "{{.Names}}" | grep -q "$name"; then
        echo "✅ $name (端口: $port) - 运行中"
    else
        echo "❌ $name - 未运行"
    fi
}

check_service "ecommerce-mysql" "3306"
check_service "ecommerce-redis" "6379"
check_service "ecommerce-nacos" "8848"
check_service "ecommerce-rocketmq-namesrv" "9876"
check_service "ecommerce-rocketmq-broker" "10911"
check_service "ecommerce-rocketmq-dashboard" "8085"

echo ""
echo "========================================="
echo "🌐 访问地址"
echo "========================================="
echo "• Nacos 控制台: http://localhost:8848/nacos (用户名/密码: nacos/nacos)"
echo "• RocketMQ Dashboard: http://localhost:8085"
echo "• MySQL: localhost:3306 (root/root)"
echo "• Redis: localhost:6379"
echo ""
echo "💡 提示: 使用 docker-compose logs -f [服务名] 查看日志"
echo "💡 提示: 使用 docker-compose down 停止所有服务"
echo ""

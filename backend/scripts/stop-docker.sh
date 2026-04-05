#!/bin/bash

echo "========================================="
echo "电商秒杀系统 - Docker 环境停止脚本"
echo "========================================="

docker-compose down

echo ""
echo "✅ 所有服务已停止"
echo "💡 提示: 保留数据使用 docker-compose down"
echo "💡 提示: 删除数据使用 docker-compose down -v"
echo ""

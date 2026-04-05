.PHONY: help docker-up docker-down docker-logs docker-restart
.PHONY: dev-gateway dev-store dev-order dev-pay dev-email dev-all
.PHONY: stop-gateway stop-store stop-order stop-pay stop-email stop-all
.PHONY: status

# 颜色定义
RED     := \033[0;31m
GREEN   := \033[0;32m
YELLOW  := \033[0;33m
BLUE    := \033[0;34m
NC      := \033[0m

help:
	@echo "$(BLUE)========================================$(NC)"
	@echo "$(BLUE)   电商秒杀系统 - Makefile 帮助$(NC)"
	@echo "$(BLUE)========================================$(NC)"
	@echo ""
	@echo "$(GREEN)Docker 相关:$(NC)"
	@echo "  make docker-up       - 启动所有 Docker 容器"
	@echo "  make docker-down     - 停止所有 Docker 容器"
	@echo "  make docker-restart  - 重启 Docker 容器"
	@echo "  make docker-logs     - 查看 Docker 日志"
	@echo ""
	@echo "$(GREEN)开发运行 (前台显示日志):$(NC)"
	@echo "  make dev-gateway     - 启动 Gateway (端口 8080)"
	@echo "  make dev-store      - 启动 Store (端口 8081)"
	@echo "  make dev-order      - 启动 Order (端口 8082)"
	@echo "  make dev-pay        - 启动 Pay (端口 8083)"
	@echo "  make dev-email      - 启动 Email (端口 8084)"
	@echo ""
	@echo "$(GREEN)停止服务:$(NC)"
	@echo "  make stop-gateway    - 停止 Gateway"
	@echo "  make stop-store      - 停止 Store"
	@echo "  make stop-order      - 停止 Order"
	@echo "  make stop-pay        - 停止 Pay"
	@echo "  make stop-email      - 停止 Email"
	@echo "  make stop-all        - 停止所有服务"
	@echo ""
	@echo "$(GREEN)其他:$(NC)"
	@echo "  make status          - 查看所有服务状态"
	@echo "  make build           - 编译所有项目"
	@echo ""

# ==================== Docker 相关 ====================

docker-up:
	@echo "$(GREEN)启动 Docker 容器...$(NC)"
	docker-compose up -d
	@echo "$(GREEN)Docker 容器已启动$(NC)"
	@docker ps --format "table {{.Names}}\t{{.Status}}"

docker-down:
	@echo "$(YELLOW)停止 Docker 容器...$(NC)"
	docker-compose down
	@echo "$(GREEN)Docker 容器已停止$(NC)"

docker-restart:
	$(MAKE) docker-down
	$(MAKE) docker-up

docker-logs:
	docker-compose logs -f

# ==================== 编译 ====================

build:
	@echo "$(GREEN)编译项目...$(NC)"
	mvn clean package -DskipTests
	@echo "$(GREEN)编译完成!$(NC)"

# ==================== 开发运行 (前台日志) ====================

dev-gateway:
	@echo "$(GREEN)启动 Gateway...$(NC)"
	cd ecommerce-gateway && mvn spring-boot:run

dev-store:
	@echo "$(GREEN)启动 Store...$(NC)"
	cd ecommerce-store && mvn spring-boot:run

dev-order:
	@echo "$(GREEN)启动 Order...$(NC)"
	cd ecommerce-order && mvn spring-boot:run

dev-pay:
	@echo "$(GREEN)启动 Pay...$(NC)"
	cd ecommerce-pay && mvn spring-boot:run

dev-email:
	@echo "$(GREEN)启动 Email...$(NC)"
	cd ecommerce-email && mvn spring-boot:run

# ==================== 停止服务 ====================

stop-gateway:
	@echo "$(YELLOW)停止 Gateway...$(NC)"
	@pkill -f "ecommerce-gateway" || true
	@echo "$(GREEN)Gateway 已停止$(NC)"

stop-store:
	@echo "$(YELLOW)停止 Store...$(NC)"
	@pkill -f "ecommerce-store" || true
	@echo "$(GREEN)Store 已停止$(NC)"

stop-order:
	@echo "$(YELLOW)停止 Order...$(NC)"
	@pkill -f "ecommerce-order" || true
	@echo "$(GREEN)Order 已停止$(NC)"

stop-pay:
	@echo "$(YELLOW)停止 Pay...$(NC)"
	@pkill -f "ecommerce-pay" || true
	@echo "$(GREEN)Pay 已停止$(NC)"

stop-email:
	@echo "$(YELLOW)停止 Email...$(NC)"
	@pkill -f "ecommerce-email" || true
	@echo "$(GREEN)Email 已停止$(NC)"

stop-all:
	@echo "$(YELLOW)停止所有服务...$(NC)"
	@pkill -f "ecommerce-" || true
	@echo "$(GREEN)所有服务已停止$(NC)"

# ==================== 服务状态 ====================

status:
	@echo "$(BLUE)========== Docker 容器状态 ==========$(NC)"
	@docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
	@echo ""
	@echo "$(BLUE)========== Java 服务状态 ==========$(NC)"
	@ps aux | grep java | grep ecommerce | grep -v grep || echo "$(YELLOW)没有运行中的电商服务$(NC)"
	@echo ""
	@echo "$(BLUE)========== 端口监听状态 ==========$(NC)"
	@ss -tlnp 2>/dev/null | grep -E "808[0-4]|3306|6379|8848|9876" || netstat -tlnp 2>/dev/null | grep -E "808[0-4]|3306|6379|8848|9876" || echo "端口检查工具不可用"

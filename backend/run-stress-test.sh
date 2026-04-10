#!/bin/bash

# 秒杀系统压测脚本
# 包含Jmeter安装和压测执行

set -e

echo "========== 秒杀系统压测准备 =========="

# 1. 检查并安装Jmeter
install_jmeter() {
    if command -v jmeter &> /dev/null; then
        echo "Jmeter已安装: $(jmeter --version | head -n 1)"
        return 0
    fi

    echo "开始安装Jmeter..."

    # 检查Java环境
    if ! command -v java &> /dev/null; then
        echo "错误: 未找到Java环境，请先安装JDK 8+"
        exit 1
    fi

    JMETER_VERSION="5.6.3"
    JMETER_HOME="$HOME/apache-jmeter-$JMETER_VERSION"

    if [ ! -d "$JMETER_HOME" ]; then
        echo "下载Jmeter $JMETER_VERSION..."
        wget -q --show-progress https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$JMETER_VERSION.tgz \
            -O /tmp/apache-jmeter-$JMETER_VERSION.tgz

        echo "解压Jmeter..."
        tar -xzf /tmp/apache-jmeter-$JMETER_VERSION.tgz -C ~/

        rm /tmp/apache-jmeter-$JMETER_VERSION.tgz
    fi

    export JMETER_HOME
    export PATH="$JMETER_HOME/bin:$PATH"

    echo "Jmeter安装完成: $(jmeter --version | head -n 1)"
}

# 2. 启动Docker服务（如果未启动）
start_docker_services() {
    echo "检查Docker服务状态..."
    if docker compose ps | grep -q "Up"; then
        echo "Docker服务已启动"
    else
        echo "启动Docker服务..."
        docker compose up -d
        echo "等待服务启动..."
        sleep 10
    fi
}

# 3. 初始化测试数据
init_test_data() {
    echo "初始化测试数据..."

    # 初始化库存
    echo "设置秒杀库存为100件..."
    curl -s -X POST http://localhost:8081/api/store/init/stock \
        -H "Content-Type: application/json" \
        -d '{"productId": 1, "stock": 100}' > /dev/null

    # 清空用户购买记录（测试用）
    echo "清空Redis测试数据..."
    docker exec ecommerce-redis redis-cli DEL "stock:seckill:1" "user:bought:1" > /dev/null 2>&1 || true

    # 重新设置库存
    docker exec ecommerce-redis redis-cli SET "stock:seckill:1" 100 > /dev/null 2>&1 || true

    echo "测试数据初始化完成"
}

# 4. 执行压测
run_stress_test() {
    export JMETER_HOME="$HOME/apache-jmeter-5.6.3"
    export PATH="$JMETER_HOME/bin:$PATH"

    TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    echo "========== 开始压测 =========="
    echo "测试场景："
    echo "  - 秒杀下单: 100并发用户，10秒内启动"
    echo "  - 支付创建: 50并发用户，5秒内启动"
    echo ""

    # 运行测试
    jmeter -n \
        -t "$TEST_DIR/jmeter-test-plan.jmx" \
        -l "$TEST_DIR/results.jtl" \
        -j "$TEST_DIR/jmeter.log" \
        -e \
        -o "$TEST_DIR/html-report"

    echo ""
    echo "========== 压测完成 =========="
    echo "结果文件："
    echo "  - 日志: $TEST_DIR/jmeter.log"
    echo "  - 结果: $TEST_DIR/results.jtl"
    echo "  - HTML报告: $TEST_DIR/html-report/index.html"
}

# 5. 分析结果
analyze_results() {
    export JMETER_HOME="$HOME/apache-jmeter-5.6.3"
    export PATH="$JMETER_HOME/bin:$PATH"

    TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    echo ""
    echo "========== 结果分析 =========="

    if [ -f "$TEST_DIR/results.jtl" ]; then
        echo "聚合报告："
        jmeter -g "$TEST_DIR/results.jtl" -o "$TEST_DIR/report-temp" > /dev/null 2>&1 || true

        # 统计信息
        TOTAL_REQUESTS=$(grep -c "true" "$TEST_DIR/results.jtl" 2>/dev/null || echo "0")
        FAILED_REQUESTS=$(grep -c "false" "$TEST_DIR/results.jtl" 2>/dev/null || echo "0")
        SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", ($TOTAL_REQUESTS - $FAILED_REQUESTS) / $TOTAL_REQUESTS * 100}")

        echo "  总请求数: $TOTAL_REQUESTS"
        echo "  失败请求: $FAILED_REQUESTS"
        echo "  成功率: $SUCCESS_RATE%"

        # 检查Redis库存
        echo ""
        echo "Redis库存检查："
        REMAINING_STOCK=$(docker exec ecommerce-redis redis-cli GET "stock:seckill:1" 2>/dev/null || echo "N/A")
        echo "  剩余库存: $REMAINING_STOCK"

        echo ""
        echo "详细报告已生成: $TEST_DIR/html-report/index.html"
    else
        echo "未找到结果文件"
    fi
}

# 主流程
main() {
    install_jmeter
    start_docker_services
    init_test_data
    run_stress_test
    analyze_results
}

# 显示帮助
show_help() {
    echo "秒杀系统压测脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项："
    echo "  install    仅安装Jmeter"
    echo "  prepare    仅初始化测试数据"
    echo "  test       仅运行压测"
    echo "  analyze    仅分析结果"
    echo "  all        运行完整流程（默认）"
    echo "  help       显示帮助"
    echo ""
    echo "示例："
    echo "  $0 all     # 完整流程"
    echo "  $0 test    # 仅运行压测"
}

# 根据参数执行
case "${1:-all}" in
    install)
        install_jmeter
        ;;
    prepare)
        start_docker_services
        init_test_data
        ;;
    test)
        run_stress_test
        ;;
    analyze)
        analyze_results
        ;;
    all)
        main
        ;;
    help)
        show_help
        ;;
    *)
        echo "未知选项: $1"
        show_help
        exit 1
        ;;
esac

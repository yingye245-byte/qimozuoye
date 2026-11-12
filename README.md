# 校园二手闲置交易平台（campus-secondhand）

面向高校场景的 C2C 二手闲置交易平台：学生发布闲置、管理员审核上架、买家检索浏览并在线下单、卖家确认交付、买家收货评价，串成 **发布 → 审核 → 下单 → 支付 → 确认 → 收货 → 完成** 的完整业务闭环。下单后 30 分钟未支付自动关单并恢复商品；配套卖家经营中心与管理员 ECharts 数据看板。服务端渲染单体应用，含 Redis 缓存三防、RabbitMQ 延迟取消与异步邮件、定时任务兜底、三级角色越权防御等中间件实践。

---

## 一、项目简介与技术栈

### 1.1 技术栈总览

| 层面 | 技术选型 | 用途 |
|---|---|---|
| 核心底座 | Spring Boot 3.3.5 + JDK 17 + Maven 3.9 | 应用框架与构建（满足任务书 2.7+ / JDK 17+ 要求） |
| 数据库 | MySQL 8.0.46（utf8mb4 / utf8mb4_unicode_ci） | 9 张核心表（用户/分类/商品/图片/留言/订单/浏览/收藏夹/成交统计） |
| ORM | MyBatis-Plus 3.5.7 + HikariCP | 条件构造器 + 注解自定义 SQL，全部 `#{}` 预编译，杜绝 `${}` 拼接 |
| 视图层 | Thymeleaf 3.1 + 原生 JS + ECharts 5.5（本地引入） | 服务端渲染 + 离线可用静态库（按任务书禁用 Vue/React） |
| 安全 | Spring Security 6（BCrypt + 三级角色） | `ROLE_USER` / `ROLE_SELLER` / `ROLE_ADMIN` 权限叠加，URL 规则 + `@PreAuthorize` + Service 层归属校验三重防御，CSRF 保留，会话单点控制 |
| 缓存 | Redis 5（Lettuce + opsForValue） | 商品详情 / 分类树 / 热门榜 / 浏览记录 / 看板数据；`__NULL__` 空值占位防穿透、SETNX 互斥锁防击穿、TTL 0~5 分钟随机抖动防雪崩，异常全部降级查库 |
| 消息队列 | RabbitMQ 4.4（Direct 交换机 + TTL/DLX 延迟队列） | ①30 分钟未支付延迟关单 ②下单成功异步邮件 ③订单状态变更异步通知；事务提交后（afterCommit）才投递 |
| 定时任务 | Spring Task `@Scheduled`（cron） | 每 5 分钟超时订单兜底扫描、每日 1:00 成交统计、每日 3:00 缓存清理、每 10 分钟心跳 |
| 邮件 | JavaMailSender（QQ 邮箱 SMTP） | 下单成功通知买家、商品被拍下通知卖家、服务自检接口 `/api/mail/test` |
| 可视化 | ECharts 5.5 | 后台看板：成交趋势折线 / 分类分布圆环 / 订单状态漏斗 / 卖家排行条形 |
| 部署 | Jar + Windows 本地 / Linux + systemd + Nginx | dev / test / prod 三套 profile |

### 1.2 包结构

```
com.campus.shop
├── common      # Result 统一返回 / BizException / GlobalExceptionHandler / CacheKeys / Constants / IdCodec
├── config      # SecurityConfig / RabbitConfig / RedisConfig / MybatisPlusConfig / WebMvcConfig
│               # LoginUser / LoginSuccessHandler / GlobalModelAdvice / StartupChecker / MyMetaObjectHandler
├── controller  # Home / Item / Order / User / Seller / Admin / Api / Upload / MailTest（9 个）
├── service     # CacheService / OrderStateService / MailService / CustomUserDetailsService
│   └── impl    # Item / Order / User / Extra 四个业务实现（事务、缓存同步、MQ 投递均在此层）
├── mapper      # 9 个 Mapper（含 item 乐观抢占、orders 四表 join、transaction_stat upsert 等自定义 SQL）
├── entity      # 与 9 张表一一对应
├── dto         # 入参对象（JSR-303 校验注解）
├── mq          # OrderProducer / OrderConsumer / OrderMessage
├── task        # ScheduledTasks（超时扫描 / 成交统计 / 缓存清理 / 心跳）
└── util        # OrderNoGenerator（订单号 = SO + 日期 + 毫秒 + 4 位随机）
```

统一响应契约：`Result<T>`（`code` / `message` / `data`），页面侧通过 flash 属性（`success` / `error` / `pendingMsg`）回显操作结果。

---

## 二、核心功能模块说明

### 2.1 普通用户（ROLE_USER）

| 功能 | 说明 |
|---|---|
| 首页与检索 | 分类卡片导航、热门商品、最新发布；商品列表支持关键词搜索、价格区间、排序（最新/价格升降/浏览量）、分页 |
| 商品详情 | 主图 + 多图切换、卖家信息、留言区；未成交商品可「立即购买」或加入**想要清单** |
| 在线下单 | 下单即乐观抢占商品（防超卖），生成订单号并进入待支付；RabbitMQ 同步投递 30 分钟延迟取消消息 |
| 订单支付与收货 | 待支付 → 已支付 → 卖家确认 → 买家收货 → 已完成；待支付阶段可自行取消，取消后商品自动恢复在售 |
| 个人中心 | 资料修改（昵称/邮箱）、密码修改、我的订单、想要清单、最近浏览（去重取最新一次） |
| 我要卖 | 一键申请成为卖家（普通用户 → `ROLE_SELLER`），或直接发布闲置等待审核 |

页面：`/`、`/item/list`、`/item/detail/{id}`、`/item/publish`、`/profile`、`/profile/cart`、`/order/buyer`

### 2.2 卖家端（ROLE_SELLER）

| 功能 | 说明 |
|---|---|
| 经营概览 | 发布数 / 收到订单数 / 完成数 / 累计收入 / 未读留言，数字卡片一屏汇总 |
| 我的发布 | 商品上下架、编辑、删除；**只能操作自己发布的商品**（Service 层归属校验） |
| 卖出订单 | 查看买家订单，对已支付订单执行「确认订单」，推进到待收货状态 |
| 留言管理 | 查看买家留言并回复，仅本人商品可回复；禁止给自己的商品留言（避免自问自答刷互动） |

页面：`/seller`、`/seller/items`、`/seller/comments`、`/order/seller`、`/item/publish`

### 2.3 管理端（ROLE_ADMIN）

| 功能 | 说明 |
|---|---|
| 数据看板 | 成交趋势双轴折线（单数 + 金额）、分类分布圆环、订单状态漏斗、卖家成交排行，全部走 ECharts 接口 |
| 商品审核 | 待审核列表 → 通过 / 驳回，未通过审核的商品前台不可见、无法下单 |
| 用户管理 | 用户列表、禁用 / 启用（`status` 置位后无法登录）、升级为卖家 |
| 订单管理 | 全站订单查询、订单详情；对未结束订单执行**强制取消**并记录取消原因、恢复商品在售 |
| 定时任务演示 | 页面按钮直接触发超时扫描 / 成交统计 / 缓存清理，便于答辩现场演示 Spring Task |

页面：`/admin/dashboard`、`/admin/audit`、`/admin/users`、`/admin/orders`

### 2.4 越权防御（三重防线）

1. **URL 规则**（`SecurityConfig`）：`/seller/**` 需 `SELLER|ADMIN`、`/admin/**` 需 `ADMIN`，商品浏览与登录注册放行；CSRF 仅对 `/api/**` 豁免。
2. **方法注解**：`AdminController` 类级 `@PreAuthorize("hasRole('ADMIN')")` 二次加固，即使 URL 规则被误配也会被拦截。
3. **Service 层兜底**：所有写操作在业务层按登录态 `userId` 校验数据归属——卖家只能改自己的商品、回复自己商品的留言，买家只能取消自己的订单。实测普通用户访问 `/admin/audit` 返回 403 错误页。

---

## 三、本地启动指南

### 3.1 前置条件

- JDK 17、Maven 3.6+
- MySQL 8.0、Redis、RabbitMQ（本地默认端口即可；RabbitMQ 未启动时超时取消由定时任务兜底，不影响演示）

### 3.2 导入 SQL

脚本自带建库语句，按顺序执行两步：

```bash
# ① 创建数据库与应用专用账号（campus / campus123）
mysql -uroot -p < sql/db_init_user.sql

# ② 建表 + 演示数据（9 张表，每张表不少于 5 条）
mysql -ucampus -pcampus123 < sql/campus_secondhand.sql
```

演示数据含 1 名管理员、2 名卖家、2 名普通用户及 38 件商品、22 条历史订单。**所有测试账号密码统一为明文 `123456`**（库中存储 BCrypt 密文）。

### 3.3 配置 Redis / RabbitMQ / 邮件

默认激活 `dev` profile（`application-dev.yml`），本机默认参数即可直连：

- Redis：`127.0.0.1:6379`，无密码，db 0
- RabbitMQ：`127.0.0.1:5672`，`guest/guest`，vhost `/`（交换机 / 队列 / 死信队列由应用启动时自动声明，无需手工创建；管理台 <http://localhost:15672>）
- 邮件：`spring.mail.*` 配置 QQ 邮箱 SMTP（587 + STARTTLS）；授权码未填写时只记日志、不抛异常，不影响业务

如需改连接参数，直接编辑 `application-dev.yml`；生产环境用 `application-prod.yml` 的环境变量占位（见第四章）。

### 3.4 启动项目

IDE 中直接运行 `CampusSecondhandApplication`，或命令行：

```bash
mvn clean package -DskipTests
java -jar target/campus-secondhand.jar --spring.profiles.active=dev
```

启动后访问 <http://localhost:8080>，按角色登录后自动分流（管理员 → 看板、卖家 → 卖家中心、普通用户 → 首页）。图片落盘目录为 `D:/projects/uploads`（`campus.upload.path`）。

启动时控制台会打印自检日志：

```
[自检-数据库] 连接正常，表数量 = 9，用户 8 人，在售商品 38 件
[自检-Redis] 读写正常，回写值 = dev
```

### 3.5 演示账号

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| admin | 123456 | 管理员 | 看板 / 审核 / 用户 / 订单 / 定时任务演示 |
| seller01 | 123456 | 认证卖家 | 张三，发布数码与教材 |
| seller02 | 123456 | 认证卖家 | 李四，发布生活用品与运动户外 |
| user01 | 123456 | 普通用户 | 王五，买家演示用 |
| user02 | 123456 | 普通用户 | 赵六，买家演示用 |

---

## 四、Linux 部署说明

完整运维手册见 `校园二手闲置交易平台_部署文档.docx`（JDK/MySQL/Redis/RabbitMQ 安装、环境变量注入、Nginx 反代、日志切割、验证清单与故障排查）。此处摘要关键步骤。

### 4.1 打包 jar

```bash
mvn clean package -DskipTests
# 产物：target/campus-secondhand.jar（可执行 fat jar，Thymeleaf 模板与静态资源已内封）
```

上传到服务器 `/opt/campus-secondhand/campus-secondhand.jar`；图片目录 `/data/campus/uploads`、日志目录 `/data/campus/logs` 需预先创建并授权给运行用户。

### 4.2 敏感配置经环境变量注入

生产 profile（`application-prod.yml`）的数据库 / Redis / MQ 凭据全部来自 `${DB_HOST}`、`${DB_PASSWORD}`、`${REDIS_HOST}`、`${RABBIT_HOST}` 等环境变量，建议写入 `/etc/campus-secondhand.env`（权限 600）：

```bash
DB_HOST=127.0.0.1
DB_USER=campus
DB_PASSWORD=***
REDIS_HOST=127.0.0.1
RABBIT_HOST=127.0.0.1
RABBIT_USER=campus
RABBIT_PASSWORD=***
SPRING_PROFILES_ACTIVE=prod
```

### 4.3 方式一：nohup 运行（简单场景）

```bash
set -a; . /etc/campus-secondhand.env; set +a
nohup java -jar /opt/campus-secondhand/campus-secondhand.jar \
  --spring.profiles.active=prod \
  > /data/campus/logs/nohup.out 2>&1 &
```

### 4.4 方式二：systemd 服务（推荐，开机自启 + 崩溃自动重启）

创建 `/etc/systemd/system/campus-secondhand.service`：

```ini
[Unit]
Description=campus-secondhand
After=network.target mysql.service redis.service rabbitmq-server.service

[Service]
Type=simple
User=campus
WorkingDirectory=/opt/campus-secondhand
EnvironmentFile=/etc/campus-secondhand.env
ExecStart=/usr/bin/java -jar /opt/campus-secondhand/campus-secondhand.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启用：

```bash
systemctl daemon-reload
systemctl enable --now campus-secondhand
systemctl status campus-secondhand
journalctl -u campus-secondhand -f
```

### 4.5 反向代理与日志

Nginx 反代 8080 端口，`client_max_body_size 20m;`（匹配单图 10MB 上传上限），静态 `/css/`、`/js/`、`/images/` 直接由 Nginx 输出，`/uploads/` 映射到 `/data/campus/uploads`。应用日志按 20MB 滚动、保留 30 份，可用 logrotate 每日归档。

---

## 五、项目结构速查

```
campus-secondhand/
├── pom.xml                          # Spring Boot 3.3.5 / JDK 17 / MyBatis-Plus 3.5.7
├── sql/db_init_user.sql             # 建库 + 创建应用账号 campus/campus123
├── sql/campus_secondhand.sql        # 9 张表建表语句 + 演示数据（密码 BCrypt 密文）
├── docs/verify_timeout.ps1          # 超时取消端到端验证脚本
├── README.md
└── src/main/
    ├── java/com/campus/shop/        # 66 个 Java 源文件
    └── resources/
        ├── application.yml / -dev.yml / -test.yml / -prod.yml   # 多环境配置
        ├── templates/               # Thymeleaf：layout/base.html + 19 个页面（首页/商品3/订单2/卖家3/用户2/管理4/错误2/登录注册）
        └── static/                  # css/style.css、js/、images/（含本地 ECharts 5.5）
```

---

## 六、技术亮点

1. **防超卖**：`UPDATE item SET deal_status = 2 WHERE id = ? AND deal_status = 1 AND status = 1` 条件更新抢占商品，影响行数为 0 即判定已被抢走，无需悲观锁。
2. **超时取消双保险**：RabbitMQ TTL + 死信交换机实现 30 分钟延迟关单（`campus.order.delay.queue` → DLX → `campus.order.cancel.queue`）；Spring Task 每 5 分钟兜底扫描一次，双路径都会恢复商品在售。
3. **缓存三防**：`__NULL__` 标记防穿透、`SETNX` 互斥锁防击穿、TTL 加 0~5 分钟随机抖动防雪崩；缓存读写全部 try-catch 降级查库，Redis 挂掉不影响主流程。
4. **消息可靠性**：统一 Jackson 序列化，投递时机为事务 `afterCommit`，消费端手动确认、失败只告警不抛异常，避免消息风暴。
5. **订单状态机**：0 待支付 / 1 已支付 / 2 卖家已确认 / 3 已完成 / 4 已取消 / 5 已关闭，服务层逐步校验前置状态，非法流转直接抛业务异常（实测对已完成订单点取消会被拒绝并提示原因）。
6. **安全体系**：三级角色叠加（ADMIN 含 SELLER 含 USER）+ URL 与方法级双重鉴权 + CSRF + BCrypt + 会话单点控制 + MyBatis-Plus 全量预编译。
7. **可观测**：启动自检（数据库 / Redis 读写）、全局异常分级（业务异常 WARN、资源不存在 404、参数错误 400，不打 ERROR 全栈）。
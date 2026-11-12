# quant-cd 设计说明

> 日期：2026-10-26
> 状态：待评审
> 第一期目标：个人单机系统。币安测试网现货可以真实下单；A 股可以用本地数据做条件筛选，并跑通一条双均线回测。页面能完成这些操作。

## 1. 目标与边界

`quant-cd` 是一个个人量化系统，一个 Spring Boot 进程同时提供页面和业务。

三个 Maven 模块对应三个市场：

- `crypto`：币安现货。第一期只连测试网，能查行情、查余额、市价/限价下单、撤单、刷新订单状态。下单前做交易级风控。
- `ashare`：A 股。用 Tushare 把筛选所需数据和回测日线落到本地，再在本地筛选和回测。
- `us`：美股占位。参与构建，没有业务代码，没有页面。

第一期不做：合约、主网下单、A 股实盘、多用户、WebSocket、消息队列、独立前端工程。

## 2. 全局约束

| 项 | 取值 |
|----|------|
| 语言 | Java 8 |
| 框架 | Spring Boot 2.7.18 |
| 构建 | Maven 多模块 |
| 数据库 | SQLite，文件 `./data/quant-cd.db`，Hikari 最大连接数 1，WAL |
| HTTP 客户端 | OkHttp 4.12.0 |
| 页面 | Thymeleaf + Bootstrap 5 WebJar + Chart.js WebJar，打进同一个 JAR |
| 监听 | `127.0.0.1:8080` |
| 登录 | 一个账号，Spring Security 表单登录 + Session。密码以 bcrypt 写在配置里 |
| 币安 | 默认 `https://testnet.binance.vision`。`allow-mainnet=false` 时，基地址不是这个主机就拒绝下单 |
| Tushare | `POST https://api.tushare.pro`。Token 只来自环境变量 `TUSHARE_TOKEN` |
| 币安密钥 | 环境变量 `BINANCE_API_KEY`、`BINANCE_API_SECRET` |

接口规则：业务 HTTP 接口必须被某个页面用到（表单、链接或页面里的请求）。一个页面可以使用多个接口。不为每个接口单独做页面。健康检查和静态资源不算业务接口。

模块规则：`crypto` 与 `ashare` 互不依赖。页面和安全配置只放在启动模块。交易、筛选、回测不引用 Thymeleaf。

## 3. 页面与接口

导航六个业务页，加登录页。

| 页面 | 路径 | 页面上能做的事 |
|------|------|----------------|
| 登录 | `/login` | 登录 |
| 总览 | `/` | 看现货余额、最近 10 笔订单、最近一次回测摘要、最近一次筛选的通过数量。数字由本页控制器直接读服务，不另做 JSON |
| 现货 | `/crypto` | 看行情和余额，市价/限价下单，撤单，对状态不明的订单向交易所刷新 |
| 风控 | `/risk` | 查看并保存四项交易级风控 |
| A 股数据 | `/ashare/data` | 按代码同步筛选数据，同步全部上市股票，按代码同步日线，看同步进度和已入库日线 |
| A 股筛选 | `/ashare/screen` | 勾选条件并筛选，看通过列表、未通过数量和未参与数量。可用代码查看一只股票的年度明细。缺数据时用数据页同一个同步接口补数 |
| A 股回测 | `/ashare/backtest` | 选已有日线的股票，跑双均线，看交易明细和净值曲线。没有日线时调用日线同步 |

`us` 没有页面。

业务接口和调用它的页面：

| 接口 | 调用页面 |
|------|----------|
| `GET /` | 总览 |
| `GET /crypto` | 现货、导航 |
| `POST /crypto/orders` | 现货下单表单 |
| `POST /crypto/orders/{id}/cancel` | 现货撤单按钮 |
| `POST /crypto/orders/{id}/refresh` | 现货「向交易所刷新」按钮 |
| `GET /risk` | 风控、导航 |
| `POST /risk` | 风控保存表单 |
| `GET /ashare/data` | A 股数据、导航 |
| `POST /ashare/sync/screen` | A 股数据的两个同步按钮；A 股筛选在数据不全时的补同步表单 |
| `POST /ashare/sync/daily` | A 股数据的日线按钮；A 股回测在缺少日线时的同步表单 |
| `GET /ashare/sync/{id}` | A 股数据、A 股筛选页面里的进度请求 |
| `GET /ashare/screen` | A 股筛选、导航 |
| `POST /ashare/screen` | A 股筛选表单 |
| `GET /ashare/backtest` | A 股回测、导航 |
| `POST /ashare/backtest` | A 股回测运行表单 |
| `GET /ashare/backtest/{id}/equity` | A 股回测页 Chart.js |
| `POST /logout` | 布局里的退出 |

每个 POST 表单带 CSRF 令牌。同步进度和净值是页面脚本会请求的 JSON，其余命令用表单提交后重定向回原页面，并用闪存消息显示结果。

密钥未配置时，现货页仍能打开，下单按钮不可用，并显示「未配置币安密钥」。Token 未配置时，同步按钮不可用，筛选和回测仍可读已有本地数据。

## 4. 包目录

```
quant-cd/
├── pom.xml                          # 父工程，packaging=pom，Java 1.8
├── docs/superpowers/specs/
│   └── 2026-10-26-quant-cd-design.md
├── quant-cd-common/
│   └── src/main/java/com/quantcd/common/
│       └── exception/BizException.java
├── quant-cd-crypto/
│   └── src/main/java/com/quantcd/crypto/
│       ├── exchange/
│       │   ├── BinanceSpotClient.java    # 签名、限流、现货 REST
│       │   └── SymbolRule.java           # 价格、数量步长
│       ├── market/MarketService.java
│       ├── account/AccountService.java
│       ├── risk/
│       │   ├── RiskConfig.java
│       │   ├── RiskConfigStore.java
│       │   └── TradeRiskChecker.java
│       └── order/
│           ├── Order.java
│           ├── OrderStore.java
│           └── OrderService.java
├── quant-cd-ashare/
│   └── src/main/java/com/quantcd/ashare/
│       ├── tushare/TushareClient.java
│       ├── data/
│       │   ├── SyncJobService.java
│       │   ├── ScreenDataSyncService.java
│       │   ├── DailyBarSyncService.java
│       │   └── YearStatBuilder.java
│       ├── screen/
│       │   ├── ScreenCondition.java
│       │   ├── PositiveReturnCondition.java
│       │   ├── CashDividendCondition.java
│       │   ├── PositiveProfitCondition.java
│       │   └── ScreenService.java
│       └── backtest/
│           ├── MaCrossBacktest.java
│           └── BacktestService.java
├── quant-cd-us/
│   ├── pom.xml
│   └── src/main/java/com/quantcd/us/package-info.java
└── quant-cd-app/
    └── src/main/
        ├── java/com/quantcd/
        │   ├── QuantCdApplication.java
        │   ├── config/SecurityConfig.java
        │   └── web/
        │       ├── OverviewController.java
        │       ├── CryptoController.java
        │       ├── RiskController.java
        │       ├── AshareDataController.java
        │       ├── AshareScreenController.java
        │       └── AshareBacktestController.java
        └── resources/
            ├── application.yml
            ├── schema.sql
            ├── data.sql
            └── templates/
                ├── layout.html
                ├── login.html
                ├── overview.html
                ├── crypto.html
                ├── risk.html
                ├── ashare-data.html
                ├── ashare-screen.html
                └── ashare-backtest.html
```

依赖方向：

```
quant-cd-app → quant-cd-crypto → quant-cd-common
             → quant-cd-ashare → quant-cd-common
quant-cd-us  不依赖其他模块，app 也不依赖它
```

`quant-cd-us` 保留模块和 `package-info.java`，这样目录能进入版本库，父工程构建也不会缺模块。里面不写业务类。

数据访问用 `JdbcTemplate`，放在对应模块的 Store / Service 里。不引入 JPA。Hibernate 5 没有可靠的 SQLite 方言，这套表也都是明确的 SQL。

一份 `schema.sql` 放在 `quant-cd-app`，启动时 `CREATE TABLE IF NOT EXISTS`。表的归属写在脚本注释里。

## 5. 技术方案

### 5.1 进程与页面

一个可执行 JAR。启动模块扫描 `com.quantcd`。

Thymeleaf 负责页面，Bootstrap 和 Chart.js 用 WebJar 引用，页面不依赖外网 CDN。现货、风控、筛选、回测的命令都是表单。每个命令表单在渲染时带一个隐藏的提交令牌，重复提交命中唯一索引后不再执行第二次外部调用。同步可能跑几十分钟，页面用 `GET /ashare/sync/{id}` 看进度。回测净值用 `GET /ashare/backtest/{id}/equity` 画图。

登录成功后进入总览。未登录访问业务路径时进入登录页。

### 5.2 币安现货

`BinanceSpotClient` 只实现这些 REST：

| 用途 | 方法与路径 |
|------|------------|
| 服务器时间 | `GET /api/v3/time` |
| 交易规则 | `GET /api/v3/exchangeInfo` |
| 最新价 | `GET /api/v3/ticker/price` |
| 下单 | `POST /api/v3/order` |
| 撤单 | `DELETE /api/v3/order` |
| 按客户端订单号查询 | `GET /api/v3/order` |
| 账户 | `GET /api/v3/account` |

签名是 HMAC-SHA256，参数带 `timestamp` 和 `recvWindow=5000`。本机时间与服务器时间的差值在下单前校正。请求权重用一个进程内计数器限制，超过则本地下一分钟再发，避免触发封禁。

交易规则缓存 1 小时。下单前按 `tickSize`、`stepSize` 向下取整。取整后小于 `minQty` 或 `minNotional`，页面提示并拒绝，不请求币安。

客户端订单号在现货页渲染时生成，放进表单隐藏域，并作为 `newClientOrderId` 交给币安。字符集满足币安要求（字母、数字、中划线、下划线，最长 36）。`crypto_order.client_order_id` 有唯一索引。

下单顺序：先做参数和风控检查；通过后插入 `PENDING`。插入成功才请求币安。唯一索引冲突说明同一次表单已经落过库，本次不再请求币安，页面显示已有订单。网络超时后禁止换新订单号重试，只能按原订单号刷新。

订单状态：`PENDING`、`NEW`、`PARTIALLY_FILLED`、`FILLED`、`CANCELED`、`REJECTED`、`EXPIRED`、`UNKNOWN`。交易所明确拒绝则写 `REJECTED` 和错误信息。发出后没有拿到结果则写 `UNKNOWN`。刷新时交易所没有这张单，把本地 `PENDING` 或 `UNKNOWN` 改为 `REJECTED`，原因是「提交中断，请重新下单」。重新下单必须重新打开页面，使用新的订单号。

### 5.3 交易级风控

只做四项，保存在 `crypto_risk_config` 的一行里。保存后下一次下单立即生效。

| 配置 | 默认 | 规则 |
|------|------|------|
| `max_notional` | 200 USDT | 市价用最新价估算名义金额，限价用委托价。超过则拒绝 |
| `max_qty` | 1 | 按标的资产数量，超过则拒绝 |
| `max_orders_per_minute` | 5 | 统计最近 60 秒已落库订单，超过则拒绝 |
| `min_interval_ms` | 3000 | 距上一笔订单不足间隔则拒绝 |

风控拒绝不产生 `UNKNOWN`，订单不发给币安。页面显示具体原因。

`allow-mainnet=false` 且基地址主机不是 `testnet.binance.vision` 时，下单直接拒绝。这道检查在风控之前。

### 5.4 A 股数据

Tushare 调用是 JSON：`api_name`、`token`、`params`、`fields`。每分钟请求数默认 180，可配置。限流错误和网络错误最多重试 3 次，间隔 1 秒、2 秒、4 秒。其他业务错误不重试，写入任务失败原因。

同步在单线程后台执行。同一时刻只允许一个同步任务。再次点击时，页面提示当前任务仍在进行。任务状态写入 `ashare_sync_job`。已完成的步骤写入 `ashare_sync_checkpoint`，重复运行会跳过。

筛选数据任务按这个顺序执行，进度文字写进任务的 `message`：

1. 刷新 `stock_basic`。
2. 拉取交易日历。
3. 按交易日拉取年末价。每完成一个交易日写一条检查点，`dataset=YEAR_END`，`item_key` 为交易日。
4. 按股票拉取分红和年报。页面填了代码就只拉这些代码，否则拉 `list_status=L` 的全部股票。每完成一只股票的一个接口写一条检查点。进度的 `total` 是股票数，`finished` 是分红和年报都已完成的股票数。
5. 重算本次涉及股票的 `ashare_year_stat`。

指定代码时，股票列表、交易日历和年末价仍然拉全市场。这三步调用次数少，能让收益条件覆盖全部股票。分红和年报才按代码缩小范围。

日线是另一类任务：按一只股票的起止日期调用 `daily` 和 `adj_factor`，供回测使用。筛选不读全量日线。

筛选数据的同步窗口固定为最近 10 个已结束自然年，再加前一年的年末价。页面上的年数 N 只决定筛选看哪几年，不改变同步范围。今天是 2026-10-26 时，收益年是 2016 到 2025，年末价从 2015 取到 2025。

筛选数据的拉法：

| 数据 | 接口 | 拉法 |
|------|------|------|
| 股票列表 | `stock_basic` | 一次 |
| 交易日历 | `trade_cal`，`exchange=SSE` | 从「当前年 - 11」的 1 月 1 日到去年 12 月 31 日 |
| 年末价 | `daily`、`adj_factor` | 窗口内每个已结束自然年，从最后一个开市日往前最多 5 个开市日，按日拉全市场。每只股票取其中最晚的一条 |
| 分红 | `dividend` | 按股票拉，已完成的跳过 |
| 年报净利润 | `income` | 按股票拉，已完成的跳过 |

年度统计由 `YearStatBuilder` 写入 `ashare_year_stat`。年末价批次完成后重算全部股票的复权价和年度收益；某只股票的分红或年报写入后，重算这只股票的分红和净利润。

字段口径：

- 年末复权价 = `close * adj_factor`。
- 年度收益 = 本年年末复权价 / 上年年末复权价 - 1。5 个开市日内没有成交，该年价格为空。
- 现金分红年份取 `end_date` 的年份。只统计 `div_proc=实施`。金额用 `cash_div`，为空则用 `cash_div_tax`。同一年多条记录相加。这个金额只判断有没有分红，不加入股价收益，避免复权价和分红算两次。
- 净利润取 `end_date` 以 `1231` 结尾、`report_type` 为 `1` 或 `4` 的记录。同一年多条时取 `ann_date` 最新的 `n_income_attr_p`。

### 5.5 筛选

筛选只读 `ashare_year_stat` 和同步检查点，请求过程中不访问 Tushare。

页面传入年数 N（3 到 10，默认 5）和勾选的条件。至少勾选一项。多个条件同时满足才通过。年份是最近 N 个已结束自然年，不含当年。

| 条件类 | 通过标准 |
|--------|----------|
| `PositiveReturnCondition` | 这 N 年的年度收益都存在且大于 0 |
| `CashDividendCondition` | 该股票分红已同步，且这 N 年每年现金分红合计大于 0 |
| `PositiveProfitCondition` | 该股票年报已同步，且这 N 年每年归母净利润大于 0 |

判定分三种结果：

- 通过：勾选的条件全部满足。
- 不通过：相关数据已经同步，但条件不满足。分红已同步且该年没有实施记录，算没有分红。年报已同步且该年没有年报，算净利润不满足。年末价批次已经完成，但该股票该年没有价格，算收益不满足（当年未上市，或年末连续停牌超过 5 个交易日）。
- 未参与：勾选了分红或净利润，而这只股票还没有对应检查点。

勾选了收益条件，但所需交易日还有 `YEAR_END` 检查点缺失时，整次筛选不执行，页面提示先同步筛选数据。

结果写入 `ashare_screen_run` 和 `ashare_screen_hit`，每只被评估的股票一行，`outcome` 为 `PASS`、`FAIL` 或 `SKIP`。筛选页默认显示通过列表。同一页可以用代码查出这只股票的结果和每年明细，仍使用 `GET /ashare/screen`。总览只显示最近一次运行的通过数量和条件摘要。

新增条件时，增加一个 `ScreenCondition` 实现和筛选页上的一个勾选。仍使用 `POST /ashare/screen`。

### 5.6 回测

`MaCrossBacktest` 对一只股票的日线做双均线。默认快线 5、慢线 20，页面可改，要求 `2 <= 快线 < 慢线`。初始资金默认 100000，页面可改。

价格序列使用 `open * adj_factor` 和 `close * adj_factor`。收盘价算出当日均线。金叉后，下一日开盘价用全部现金买入。死叉后，下一日开盘价全部卖出。同一时间只持有这一只股票，不做空。

费用默认值：买卖佣金各 0.00025，卖出印花税 0.0005，没有最低 5 元。费用写在配置里，页面展示本次使用的费率。

输出：交易明细、每日净值、总收益率、最大回撤。结果入库。区间内日线有缺失交易日时拒绝运行，页面提示先同步日线。缺失的判断依据是本地日线日期相对 `trade_cal` 里该区间的开市日。

### 5.7 表结构

`crypto_risk_config`

- `id` 固定 1
- `max_notional`、`max_qty`、`max_orders_per_minute`、`min_interval_ms`
- `updated_at`

`crypto_order`

- `id`、`client_order_id` 非空、`exchange_order_id`
- `symbol`、`side`（BUY/SELL）、`type`（MARKET/LIMIT）
- `price`、`quantity`、`executed_qty`、`quote_qty`
- `status`、`error_message`
- `created_at`、`updated_at`

`ashare_stock`

- 主键 `ts_code`
- `symbol`、`name`、`market`、`list_date`、`list_status`

`ashare_trade_cal`

- 主键 `cal_date`
- `is_open`
- 同步交易日历时写入。回测用它判断日线是否缺了开市日

`ashare_daily`

- 主键 (`ts_code`, `trade_date`)
- `open`、`high`、`low`、`close`、`vol`、`amount`、`adj_factor`

`ashare_dividend`

- 主键 (`ts_code`, `end_date`, `ann_date`, `div_proc`)
- `cash_div`、`cash_div_tax`、`ex_date`

`ashare_income`

- 主键 (`ts_code`, `end_date`, `ann_date`, `report_type`)
- `n_income_attr_p`

`ashare_year_stat`

- 主键 (`ts_code`, `year`)
- `adj_close`、`annual_return`、`cash_div`、`net_profit`

`ashare_sync_job`

- `id`、`submit_token` 非空
- `job_type`（SCREEN_DATA / DAILY_BAR）
- `status`（RUNNING / SUCCESS / FAILED）
- `total`、`finished`、`message`
- `started_at`、`finished_at`

`ashare_sync_checkpoint`

- 主键 (`dataset`, `item_key`)
- `synced_at`
- `YEAR_END` 的 `item_key` 是交易日
- `DIVIDEND`、`INCOME`、`DAILY_BAR` 的 `item_key` 是 `ts_code`

`ashare_backtest`

- `id`、`submit_token` 非空
- `ts_code`、`start_date`、`end_date`
- `fast_period`、`slow_period`、`initial_cash`
- `total_return`、`max_drawdown`
- `trades_json`、`equity_json`、`created_at`

`ashare_screen_run`

- `id`、`submit_token` 非空
- `year_count`、`conditions`、`pass_count`、`skipped_count`、`created_at`

`ashare_screen_hit`

- `id`、`run_id`、`ts_code`、`name`、`outcome`（`PASS` / `FAIL` / `SKIP`）、`detail`

金额、价格、收益使用十进制字符串列，Java 侧用 `BigDecimal`。不用浮点列。

#### 唯一索引

业务主键本身就是唯一约束。行情、分红、年报、年度统计和检查点重复写入时用 `ON CONFLICT DO UPDATE`，不新插一行。

| 表 | 唯一索引 | 冲突时 |
|----|----------|--------|
| `crypto_risk_config` | 主键 `id`，并 `CHECK (id = 1)` | 只有一行。保存风控是更新，重复提交覆盖同一行 |
| `crypto_order` | `uk_crypto_order_client_order_id(client_order_id)` | 同一次下单表单不再请求币安，页面打开已有订单 |
| `crypto_order` | `uk_crypto_order_exchange_order_id(exchange_order_id) WHERE exchange_order_id IS NOT NULL` | 同一个交易所订单号不能挂到两行本地订单 |
| `ashare_stock` | 主键 `ts_code` | 覆盖名称和上市状态 |
| `ashare_trade_cal` | 主键 `cal_date` | 同一交易日只保留一行 |
| `ashare_daily` | 主键 `(ts_code, trade_date)` | 覆盖这一根 K 线 |
| `ashare_dividend` | 主键 `(ts_code, end_date, ann_date, div_proc)` | 覆盖分红金额 |
| `ashare_income` | 主键 `(ts_code, end_date, ann_date, report_type)` | 覆盖净利润 |
| `ashare_year_stat` | 主键 `(ts_code, year)` | 覆盖该年统计 |
| `ashare_sync_checkpoint` | 主键 `(dataset, item_key)` | 覆盖同步时间 |
| `ashare_sync_job` | `uk_sync_job_submit_token(submit_token)` | 同一次同步表单返回已有任务，不启动第二个 |
| `ashare_sync_job` | `uk_sync_job_one_running(status) WHERE status = 'RUNNING'` | 全库同时只能有一个进行中的同步 |
| `ashare_backtest` | `uk_backtest_submit_token(submit_token)` | 同一次回测表单返回已有结果 |
| `ashare_screen_run` | `uk_screen_run_submit_token(submit_token)` | 同一次筛选表单返回已有结果 |
| `ashare_screen_hit` | `uk_screen_hit_run_code(run_id, ts_code)` | 同一次筛选里一只股票只有一行 |

`submit_token` 和 `client_order_id` 都是非空列。SQLite 的唯一索引对多个 `NULL` 不冲突，所以这两列不能为空。

命令令牌在对应页面每次 `GET` 时生成，写入隐藏域。用户重新打开页面得到新令牌，从而可以再次下单、再次同步、再次筛选或再次回测。相同参数的再次运行是新的一次提交，不受上一次令牌限制。

同步任务先插入 `RUNNING` 行。`submit_token` 冲突就转去看那条已有任务。`RUNNING` 部分唯一索引冲突就告诉用户已有任务在跑，并打开那条任务的进度。两种冲突都不向 Tushare 再拉一次。

这套约束不依赖连接池只有 1 个连接。池子以后变大，重复提交仍然会被唯一索引挡住。

### 5.8 错误处理

| 情况 | 行为 |
|------|------|
| 风控拒绝、参数不合法、步长不合法 | 不访问交易所。页面闪存具体原因 |
| 币安返回业务错误 | 订单 `REJECTED`，页面显示币安错误信息 |
| 下单请求超时或连接失败 | 订单 `UNKNOWN`。页面提示先刷新，刷新按钮按 `client_order_id` 查询 |
| 撤单时交易所已经成交 | 把本地状态更新为 `FILLED`，页面说明已成交 |
| Tushare 限流或网络失败 | 同步任务内重试 3 次，仍失败则任务 `FAILED`，已写入的检查点保留 |
| 已有同步任务在跑 | `RUNNING` 唯一索引插入失败。页面打开正在跑的任务，不启动第二个 |
| 同一个提交令牌再次入库 | 唯一索引插入失败。不请求币安，也不新开同步、筛选或回测 |
| 筛选一个条件都没选，或 N 不在 3 到 10 | 不跑筛选，页面提示 |
| 回测日线缺口、快慢线不合法 | 不写回测结果，页面提示 |
| 未登录 | 转到登录页 |

日志不打印密钥、Secret、签名和 Tushare Token。

### 5.9 配置

`application.yml` 只引用环境变量，不写真实密钥。

```yaml
server:
  address: 127.0.0.1
  port: 8080
spring:
  datasource:
    url: jdbc:sqlite:./data/quant-cd.db
    hikari:
      maximum-pool-size: 1
      connection-init-sql: PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000
quantcd:
  auth:
    username: admin
    password: "{noop}admin"
  crypto:
    base-url: https://testnet.binance.vision
    api-key: ${BINANCE_API_KEY:}
    api-secret: ${BINANCE_API_SECRET:}
    allow-mainnet: false
  ashare:
    token: ${TUSHARE_TOKEN:}
    permits-per-minute: 180
    commission-rate: "0.00025"
    stamp-tax-rate: "0.0005"
```

启动时执行 `schema.sql`。`data.sql` 用 `INSERT OR IGNORE` 写入 `id=1` 的风控默认行，重复启动不会再插一行。

登录密码默认是 `admin`，配置值写成 `{noop}admin`。服务只绑定本机。要改密码时，把配置改成新的 `{noop}...`，或设置环境变量 `QUANTCD_AUTH_PASSWORD`。不要把带花括号的默认值嵌进 `${}`，Spring 会把内层括号吃掉。

## 6. 可行性

Java 8 使用 Boot 2.7.18，包名是 `javax.*`。`cuant` 的业务划分可以沿用，Boot 3 的代码不能直接编译进来。币安官方 Connector 的现行版本面向更新的 JDK，所以现货客户端按 `cuant` 已通的 REST 自己签，只保留现货下单这条链路。

SQLite 承担订单、日线和年度统计。年末价按固定窗口拉全市场，11 个年末、每年最多 5 个交易日、日线和复权两个接口，大约 110 次。分红和年报按股票拉取，全市场大约各 5000 次，每分钟 180 次时各约半小时，后台任务可中断后续跑。筛选和回测不再访问外网。同一张表单重复提交会被唯一索引挡住；重新打开页面后可以用新令牌再跑一次。

A 股实盘需要券商客户端，不放进第一期。美股只有占位模块。

## 7. 测试

- `TradeRiskChecker`：金额、数量、频率、间隔，以及主网开关。
- `MaCrossBacktest`：用固定日线验证金叉买入、死叉卖出、费用和最大回撤。
- `YearStatBuilder`：用固定年末价、分红和年报验证三个年度字段。
- 三个筛选条件：通过、不通过、数据未同步时不参与。
- `BinanceSpotClient`、`TushareClient`：用 MockWebServer 验证签名参数和响应解析，不访问真实网络。
- 订单服务：同一个 `client_order_id` 提交两次，第二次不调用币安，并返回第一次的订单。模拟超时后状态为 `UNKNOWN`，刷新按原客户端订单号查询。
- 同步任务：同一个 `submit_token` 不产生第二条任务；已有 `RUNNING` 时另一张表单插不进去。
- 页面测试：读取控制器映射和模板，业务映射必须出现在某个模板里。登录和静态资源除外。

真实测试网下单和真实 Tushare 同步留手动验证：配置密钥后在现货页下一笔测试网限价单并撤单；配置 Token 后同步一只股票，在筛选页看到它进入通过或未参与，再同步日线并跑出回测曲线。

## 8. 参考

- 本地 `lianghua/cuant`：现货签名、客户端订单号、下单前落库、交易级风控。
- 本地 `lianghua2/d-quant2` 的选股方式：多个条件同时满足，结果里能看到原因。筛选口径改成年度收益、现金分红和年报净利润。
- 本地 `lianghua2/duant`：个人系统用嵌入式存储，不引入 MySQL、Redis 和消息队列。
- Tushare 接口：`stock_basic`、`trade_cal`、`daily`、`adj_factor`、`dividend`、`income`。

CREATE TABLE IF NOT EXISTS crypto_risk_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    max_notional TEXT NOT NULL,
    max_qty TEXT NOT NULL,
    max_orders_per_minute INTEGER NOT NULL,
    min_interval_ms INTEGER NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS crypto_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_order_id TEXT NOT NULL,
    exchange_order_id TEXT,
    symbol TEXT NOT NULL,
    side TEXT NOT NULL,
    type TEXT NOT NULL,
    price TEXT,
    quantity TEXT NOT NULL,
    executed_qty TEXT,
    quote_qty TEXT,
    status TEXT NOT NULL,
    error_message TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_crypto_order_client_order_id ON crypto_order(client_order_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_crypto_order_exchange_order_id ON crypto_order(exchange_order_id) WHERE exchange_order_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS crypto_asset (
    asset TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    cost_price TEXT,
    profit_alerted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS crypto_market_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    asset TEXT NOT NULL,
    symbol TEXT NOT NULL,
    captured_at TEXT NOT NULL,
    price TEXT,
    bid TEXT,
    ask TEXT,
    spread TEXT,
    change_percent_24h TEXT,
    high_24h TEXT,
    low_24h TEXT,
    volume_24h TEXT,
    quote_volume_24h TEXT,
    weighted_avg_price_24h TEXT,
    day_open TEXT,
    day_high TEXT,
    day_low TEXT,
    day_volume TEXT,
    sma200 TEXT,
    rsi TEXT,
    atr TEXT,
    trend TEXT,
    direction TEXT,
    strength TEXT,
    action TEXT,
    position_percent TEXT,
    bull_score INTEGER,
    bear_score INTEGER,
    bull_detail TEXT,
    bear_detail TEXT,
    fear_greed TEXT,
    fear_label TEXT,
    account_long_short_ratio TEXT,
    account_long TEXT,
    account_short TEXT,
    top_position_ratio TEXT,
    top_position_long TEXT,
    top_position_short TEXT,
    top_account_ratio TEXT,
    top_account_long TEXT,
    top_account_short TEXT,
    taker_buy_sell_ratio TEXT,
    taker_buy_vol TEXT,
    taker_sell_vol TEXT,
    funding_rate TEXT,
    funding_avg TEXT,
    next_funding_time TEXT,
    interest_rate TEXT,
    mark_price TEXT,
    index_price TEXT,
    basis_percent TEXT,
    open_interest TEXT,
    open_interest_usdt TEXT,
    emergency INTEGER NOT NULL DEFAULT 0,
    emergency_text TEXT,
    reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_crypto_snapshot_symbol_time ON crypto_market_snapshot(symbol, captured_at);

CREATE TABLE IF NOT EXISTS ashare_stock (
    ts_code TEXT PRIMARY KEY,
    symbol TEXT,
    name TEXT,
    market TEXT,
    list_date TEXT,
    list_status TEXT
);

CREATE TABLE IF NOT EXISTS ashare_trade_cal (
    cal_date TEXT PRIMARY KEY,
    is_open INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS ashare_daily (
    ts_code TEXT NOT NULL,
    trade_date TEXT NOT NULL,
    open TEXT,
    high TEXT,
    low TEXT,
    close TEXT,
    vol TEXT,
    amount TEXT,
    adj_factor TEXT,
    PRIMARY KEY (ts_code, trade_date)
);

CREATE TABLE IF NOT EXISTS ashare_dividend (
    ts_code TEXT NOT NULL,
    end_date TEXT NOT NULL,
    ann_date TEXT NOT NULL,
    div_proc TEXT NOT NULL,
    cash_div TEXT,
    cash_div_tax TEXT,
    ex_date TEXT,
    PRIMARY KEY (ts_code, end_date, ann_date, div_proc)
);

CREATE TABLE IF NOT EXISTS ashare_income (
    ts_code TEXT NOT NULL,
    end_date TEXT NOT NULL,
    ann_date TEXT NOT NULL,
    report_type TEXT NOT NULL,
    n_income_attr_p TEXT,
    PRIMARY KEY (ts_code, end_date, ann_date, report_type)
);

CREATE TABLE IF NOT EXISTS ashare_year_stat (
    ts_code TEXT NOT NULL,
    year INTEGER NOT NULL,
    adj_close TEXT,
    annual_return TEXT,
    cash_div TEXT,
    net_profit TEXT,
    net_margin TEXT,
    gross_margin TEXT,
    roe TEXT,
    ocf_to_profit TEXT,
    PRIMARY KEY (ts_code, year)
);

CREATE TABLE IF NOT EXISTS ashare_indicator (
    ts_code TEXT NOT NULL,
    end_date TEXT NOT NULL,
    ann_date TEXT NOT NULL,
    netprofit_margin TEXT,
    grossprofit_margin TEXT,
    roe TEXT,
    ocf_to_profit TEXT,
    PRIMARY KEY (ts_code, end_date, ann_date)
);

CREATE TABLE IF NOT EXISTS ashare_sync_checkpoint (
    dataset TEXT NOT NULL,
    item_key TEXT NOT NULL,
    synced_at TEXT NOT NULL,
    PRIMARY KEY (dataset, item_key)
);

CREATE TABLE IF NOT EXISTS ashare_sync_job (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    submit_token TEXT NOT NULL,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL,
    total INTEGER NOT NULL,
    finished INTEGER NOT NULL,
    message TEXT,
    started_at TEXT NOT NULL,
    finished_at TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sync_job_submit_token ON ashare_sync_job(submit_token);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sync_job_one_running ON ashare_sync_job(status) WHERE status = 'RUNNING';

CREATE TABLE IF NOT EXISTS ashare_backtest (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    submit_token TEXT NOT NULL,
    ts_code TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    fast_period INTEGER NOT NULL,
    slow_period INTEGER NOT NULL,
    initial_cash TEXT NOT NULL,
    total_return TEXT,
    max_drawdown TEXT,
    trades_json TEXT,
    equity_json TEXT,
    created_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_backtest_submit_token ON ashare_backtest(submit_token);

CREATE TABLE IF NOT EXISTS ashare_screen_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    submit_token TEXT NOT NULL,
    year_count INTEGER NOT NULL,
    conditions TEXT NOT NULL,
    pass_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_screen_run_submit_token ON ashare_screen_run(submit_token);

CREATE TABLE IF NOT EXISTS ashare_screen_hit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id INTEGER NOT NULL,
    ts_code TEXT NOT NULL,
    name TEXT,
    outcome TEXT NOT NULL,
    detail TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_screen_hit_run_code ON ashare_screen_hit(run_id, ts_code);

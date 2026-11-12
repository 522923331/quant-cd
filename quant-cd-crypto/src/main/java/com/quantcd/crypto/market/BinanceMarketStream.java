package com.quantcd.crypto.market;

import com.quantcd.crypto.exchange.CryptoSettings;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BinanceMarketStream implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketStream.class);

    private final SnapshotService snapshots;
    private final String url;
    private final OkHttpClient http;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, LiveQuote> quotes = new ConcurrentHashMap<String, LiveQuote>();
    private final List<TickListener> listeners = new CopyOnWriteArrayList<TickListener>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Object lock = new Object();

    private WebSocket socket;
    private final Set<String> active = new LinkedHashSet<String>();
    private boolean running;
    private boolean open;
    private boolean reconnectPending;
    private int generation;
    private int retryMs = 1000;

    public BinanceMarketStream(SnapshotService snapshots, CryptoSettings settings) {
        this.snapshots = snapshots;
        this.url = MarketStreamMessages.url(settings.getBaseUrl());
        this.http = new OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build();
    }

    public LiveQuote quote(String symbol) {
        return symbol == null ? null : quotes.get(symbol.trim().toUpperCase());
    }

    public List<LiveQuote> quotes() {
        List<LiveQuote> rows = new ArrayList<LiveQuote>(quotes.values());
        Collections.sort(rows, (left, right) -> left.symbol.compareTo(right.symbol));
        return rows;
    }

    public void addListener(TickListener listener) {
        listeners.add(listener);
    }

    public void sync() {
        List<String> desired = MarketStreamMessages.streams(snapshots.watchedSymbols());
        List<String> dropped = new ArrayList<String>();
        synchronized (lock) {
            if (!running) {
                return;
            }
            if (desired.isEmpty()) {
                dropped.addAll(quotes.keySet());
                quotes.clear();
                active.clear();
                closeSocket();
            } else if (!open || socket == null) {
                connect();
            } else {
                List<String> add = new ArrayList<String>();
                List<String> remove = new ArrayList<String>();
                for (int i = 0; i < desired.size(); i++) {
                    if (!active.contains(desired.get(i))) {
                        add.add(desired.get(i));
                    }
                }
                for (String stream : active) {
                    if (!desired.contains(stream)) {
                        remove.add(stream);
                    }
                }
                if (!add.isEmpty()) {
                    socket.send(MarketStreamMessages.subscribe(ids.incrementAndGet(), add));
                    active.addAll(add);
                    log.info("订阅行情 {}", symbolsOf(add));
                }
                if (!remove.isEmpty()) {
                    socket.send(MarketStreamMessages.unsubscribe(ids.incrementAndGet(), remove));
                    active.removeAll(remove);
                    for (int i = 0; i < remove.size(); i++) {
                        String symbol = symbolOf(remove.get(i));
                        quotes.remove(symbol);
                        dropped.add(symbol);
                    }
                    log.info("取消订阅 {}", symbolsOf(remove));
                }
            }
        }
        for (int i = 0; i < dropped.size(); i++) {
            notifyGone(dropped.get(i));
        }
    }

    @Override
    public void start() {
        synchronized (lock) {
            running = true;
        }
        sync();
    }

    @Override
    public void stop() {
        synchronized (lock) {
            running = false;
            reconnectPending = false;
            closeSocket();
        }
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void connect() {
        if (socket != null) {
            return;
        }
        int gen = ++generation;
        log.info("连接币安行情 {}", url);
        WebSocket created = http.newWebSocket(new Request.Builder().url(url).build(), new Listener(gen));
        if (generation != gen) {
            created.close(1000, "bye");
            return;
        }
        socket = created;
    }

    private void closeSocket() {
        open = false;
        generation++;
        WebSocket current = socket;
        socket = null;
        active.clear();
        if (current != null) {
            current.close(1000, "bye");
        }
    }

    private void scheduleReconnect() {
        if (reconnectPending || !running) {
            return;
        }
        reconnectPending = true;
        int delay = retryMs;
        retryMs = Math.min(retryMs * 2, 30000);
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    reconnectPending = false;
                    socket = null;
                    open = false;
                    active.clear();
                }
                sync();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void accept(String text) {
        LiveQuote quote = MarketStreamMessages.parse(text);
        if (quote == null || quote.symbol == null || quote.symbol.isEmpty()) {
            return;
        }
        quotes.put(quote.symbol, quote);
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onTick(quote);
            } catch (RuntimeException ex) {
                log.warn("推送行情失败 {}", ex.getMessage());
            }
        }
    }

    private void notifyGone(String symbol) {
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onGone(symbol);
            } catch (RuntimeException ex) {
                log.warn("推送退订失败 {}", ex.getMessage());
            }
        }
    }

    private static List<String> symbolsOf(List<String> streams) {
        List<String> symbols = new ArrayList<String>();
        for (int i = 0; i < streams.size(); i++) {
            symbols.add(symbolOf(streams.get(i)));
        }
        return symbols;
    }

    private static String symbolOf(String stream) {
        int at = stream.indexOf('@');
        return (at < 0 ? stream : stream.substring(0, at)).toUpperCase();
    }

    private final class Listener extends WebSocketListener {
        private final int gen;

        private Listener(int gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            synchronized (lock) {
                if (generation != gen) {
                    webSocket.close(1000, "bye");
                    return;
                }
                socket = webSocket;
                open = true;
                retryMs = 1000;
                active.clear();
            }
            log.info("币安行情已连接");
            sync();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            accept(text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            disconnected(gen, "连接关闭 " + code);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable failure, Response response) {
            disconnected(gen, failure.getMessage());
        }
    }

    private void disconnected(int gen, String reason) {
        synchronized (lock) {
            if (generation != gen) {
                return;
            }
            generation++;
            open = false;
            socket = null;
            active.clear();
            if (!running) {
                return;
            }
            log.warn("币安行情断开 {}", reason);
            scheduleReconnect();
        }
    }
}

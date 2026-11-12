package com.quantcd.web;

import com.quantcd.crypto.market.BinanceMarketStream;
import com.quantcd.crypto.market.LiveQuote;
import com.quantcd.crypto.market.MarketStreamMessages;
import com.quantcd.crypto.market.TickListener;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TickSseHub implements TickListener, DisposableBean {

    private final BinanceMarketStream stream;
    private final List<SseEmitter> clients = new CopyOnWriteArrayList<SseEmitter>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public TickSseHub(BinanceMarketStream stream) {
        this.stream = stream;
        stream.addListener(this);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                comment("ping");
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe() {
        final SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(new Runnable() {
            @Override
            public void run() {
                clients.remove(emitter);
            }
        });
        emitter.onTimeout(new Runnable() {
            @Override
            public void run() {
                clients.remove(emitter);
            }
        });
        emitter.onError(new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                clients.remove(emitter);
            }
        });
        List<LiveQuote> current = stream.quotes();
        for (int i = 0; i < current.size(); i++) {
            try {
                emitter.send(tickEvent(MarketStreamMessages.json(current.get(i))));
            } catch (IOException ex) {
                clients.remove(emitter);
                break;
            }
        }
        return emitter;
    }

    @Override
    public void onTick(LiveQuote quote) {
        String json = MarketStreamMessages.json(quote);
        for (int i = 0; i < clients.size(); i++) {
            try {
                clients.get(i).send(tickEvent(json));
            } catch (Exception ex) {
                clients.remove(clients.get(i));
            }
        }
    }

    @Override
    public void onGone(String symbol) {
        for (int i = 0; i < clients.size(); i++) {
            try {
                clients.get(i).send(SseEmitter.event().name("gone").data(symbol, MediaType.TEXT_PLAIN));
            } catch (Exception ex) {
                clients.remove(clients.get(i));
            }
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).complete();
        }
        clients.clear();
    }

    private static SseEmitter.SseEventBuilder tickEvent(String json) {
        return SseEmitter.event().name("tick").data(json, MediaType.TEXT_PLAIN);
    }

    private void comment(String text) {
        for (int i = 0; i < clients.size(); i++) {
            try {
                clients.get(i).send(SseEmitter.event().comment(text));
            } catch (Exception ex) {
                clients.remove(clients.get(i));
            }
        }
    }
}

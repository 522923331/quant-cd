package com.quantcd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuantCdApplication {

    private static final Logger log = LoggerFactory.getLogger(QuantCdApplication.class);

    public static void main(String[] args) {
        log.info("正在启动 quant-cd");
        SpringApplication application = new SpringApplication(QuantCdApplication.class);
        application.addListeners(new ReadyLog());
        application.run(args);
    }

    static String browserUrl(String address, String port) {
        String host = address;
        if (host == null || host.isEmpty() || "0.0.0.0".equals(host) || "::".equals(host)) {
            host = "127.0.0.1";
        }
        String actualPort = port == null || port.isEmpty() ? "8080" : port;
        return "http://" + host + ":" + actualPort + "/";
    }

    private static final class ReadyLog implements ApplicationListener<ApplicationReadyEvent> {
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            Environment environment = event.getApplicationContext().getEnvironment();
            String port = environment.getProperty("local.server.port");
            if (port == null) {
                log.info("quant-cd 已启动");
                return;
            }
            log.info("quant-cd 已启动，打开 {}", browserUrl(environment.getProperty("server.address"), port));
        }
    }
}

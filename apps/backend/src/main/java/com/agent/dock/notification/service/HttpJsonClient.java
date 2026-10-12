package com.agent.dock.notification.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 알림 발송용 얇은 HTTP POST 클라이언트(JDK HttpClient — 외부 의존 없음). 실패는 예외 대신 Result 로 돌려준다.
 * 연결 5초·요청 10초 제한을 둬 발송이 요청·스케줄러 스레드를 오래 붙잡지 않게 한다.
 */
@Component
public class HttpJsonClient {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Result(boolean success, int statusCode, String body) {
    }

    public Result post(String url, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new Result(ok, response.statusCode(), response.body());
        } catch (Exception ex) {
            return new Result(false, -1, String.valueOf(ex.getMessage()));
        }
    }
}
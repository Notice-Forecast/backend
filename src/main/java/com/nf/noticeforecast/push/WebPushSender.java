package com.nf.noticeforecast.push;

import com.nf.noticeforecast.device.PushSubscription;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

/**
 * VAPID 서명·암호화는 nl.martijndwars:web-push에 맡기고, 전송은 함께 쓰는 Java HttpClient로 한다(CLAUDE.md 9.1).
 * <ul>
 *   <li>암호화는 표준(RFC 8291) aes128gcm. 라이브러리 기본 send()는 옛 aesgcm이라 애플 푸시 서버가 받지 않는다.</li>
 *   <li>유효 시간(TTL)은 24시간. 기본 28일이면 휴대폰이 오래 꺼져 있다 켜질 때 지난 "마감 임박" 알림이 뜬다.</li>
 *   <li>라이브러리 send()는 알림마다 HTTP 클라이언트를 새로 띄우고 시간 제한 없이 기다려서, 연결 5초·응답 10초 제한을 둔다.</li>
 * </ul>
 */
@Slf4j
@Component
public class WebPushSender implements PushSender {

    static final int TTL_SECONDS = 24 * 60 * 60;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** Java HttpClient가 직접 정하는 헤더라 옮기지 않는다. */
    private static final Set<String> RESTRICTED_HEADERS = Set.of("content-length", "host", "connection", "expect",
            "upgrade");

    private final VapidProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private volatile PushService pushService;

    public WebPushSender(VapidProperties properties) {
        this.properties = properties;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public Result send(PushSubscription subscription, String payloadJson) {
        if (!properties.configured()) {
            return new Result(Outcome.NOT_CONFIGURED, 0);
        }
        if (!PushEndpointPolicy.allowed(subscription.getEndpoint())) {
            log.warn("push endpoint not allowed for device {}", subscription.getDeviceId());
            return new Result(Outcome.GONE, 0);
        }
        try {
            HttpRequest request = buildRequest(service(), subscription.getEndpoint(), subscription.getP256dh(),
                    subscription.getAuth(), payloadJson);
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return new Result(Outcome.SENT, status);
            }
            if (status == 404 || status == 410) {
                return new Result(Outcome.GONE, status);
            }
            log.warn("push rejected for device {}: {}", subscription.getDeviceId(), status);
            return new Result(Outcome.FAILED, status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.FAILED, 0);
        } catch (Exception e) {
            log.warn("push failed for device {}: {}", subscription.getDeviceId(), e.toString());
            return new Result(Outcome.FAILED, 0);
        }
    }

    /** 라이브러리가 서명·암호화한 요청(헤더와 본문)을 Java HttpClient 요청으로 옮긴다. */
    static HttpRequest buildRequest(PushService service, String endpoint, String p256dh, String auth,
                                    String payloadJson) throws Exception {
        Notification notification = new Notification(endpoint, p256dh, auth,
                payloadJson.getBytes(StandardCharsets.UTF_8), TTL_SECONDS);
        HttpPost post = service.preparePost(notification, Encoding.AES128GCM);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(EntityUtils.toByteArray(post.getEntity())));
        for (Header header : post.getAllHeaders()) {
            if (!RESTRICTED_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT))) {
                builder.header(header.getName(), header.getValue());
            }
        }
        if (post.getEntity().getContentType() != null) {
            builder.header("Content-Type", post.getEntity().getContentType().getValue());
        }
        return builder.build();
    }

    private PushService service() throws Exception {
        if (pushService == null) {
            synchronized (this) {
                if (pushService == null) {
                    pushService = new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
                }
            }
        }
        return pushService;
    }
}

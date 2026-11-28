package com.nf.noticeforecast.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

/** 네트워크 없이, `npx web-push generate-vapid-keys`와 같은 형식(base64url)의 키로 서명·암호화가 되는지 본다. */
class WebPushCryptoTest {

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }

    private static String publicKey(KeyPair pair) {
        return base64Url(((ECPublicKey) pair.getPublic()).getQ().getEncoded(false));
    }

    private static String privateKey(KeyPair pair) {
        byte[] d = ((org.bouncycastle.jce.interfaces.ECPrivateKey) pair.getPrivate()).getD().toByteArray();
        byte[] fixed = new byte[32];
        System.arraycopy(d, Math.max(0, d.length - 32), fixed, Math.max(0, 32 - d.length), Math.min(32, d.length));
        return base64Url(fixed);
    }

    @Test
    void VAPID_키로_서명하고_페이로드를_암호화한다() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPair vapid = p256();
        KeyPair browser = p256();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);

        PushService service = new PushService(publicKey(vapid), privateKey(vapid), "https://github.com/Notice-Forecast");

        HttpRequest request = WebPushSender.buildRequest(service, "https://fcm.googleapis.com/fcm/send/abc",
                publicKey(browser), base64Url(auth), "{\"type\":\"DEADLINE\"}");

        assertThat(request.headers().firstValue("Authorization")).hasValueSatisfying(v -> assertThat(v).startsWith("vapid t="));
        assertThat(request.headers().firstValue("Content-Encoding")).contains("aes128gcm");
        assertThat(request.headers().firstValue("TTL")).contains("86400");
        assertThat(request.headers().allValues("Content-Type")).hasSize(1);
        assertThat(request.timeout()).contains(java.time.Duration.ofSeconds(10));
        assertThat(request.bodyPublisher()).hasValueSatisfying(body -> assertThat(body.contentLength()).isGreaterThan(0));
    }

    @Test
    void 잘못된_구독_키는_GONE으로_돌려준다() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPair vapid = p256();
        WebPushSender sender = new WebPushSender(new VapidProperties(
                publicKey(vapid), privateKey(vapid), "https://github.com/Notice-Forecast"));
        com.nf.noticeforecast.device.PushSubscription bad = org.mockito.Mockito.mock(
                com.nf.noticeforecast.device.PushSubscription.class);
        org.mockito.Mockito.when(bad.getDeviceId()).thenReturn("d1");
        org.mockito.Mockito.when(bad.getEndpoint()).thenReturn("https://fcm.googleapis.com/fcm/send/abc");
        org.mockito.Mockito.when(bad.getP256dh()).thenReturn("not-a-valid-key");
        org.mockito.Mockito.when(bad.getAuth()).thenReturn("short");

        PushSender.Result result = sender.send(bad, "{\"type\":\"DEADLINE\"}");

        assertThat(result.outcome()).isEqualTo(PushSender.Outcome.GONE);
    }
}

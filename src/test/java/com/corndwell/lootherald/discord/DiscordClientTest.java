package com.corndwell.lootherald.discord;

import com.corndwell.lootherald.history.DeliveryStatus;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordClientTest
{
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private OkHttpClient http;
    private DiscordClient client;

    @After public void cleanup()
    {
        if (client != null) { client.cancelAll(); }
        scheduler.shutdownNow();
        if (http != null) { http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
    }

    private void setup(Interceptor interceptor)
    {
        http = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        client = new DiscordClient(http, new Gson(), scheduler);
    }

    private static Response response(Request request, int code, String body)
    {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
            .body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
    }

    private CompletableFuture<DeliveryStatus> submit(byte[] screenshot)
    {
        CompletableFuture<DeliveryStatus> done = new CompletableFuture<>();
        client.submit(Destination.parse(DestinationTest.URL).get(0), DiscordFormatterTest.drop(), List.of(0),
            EmbedOptions.builder().build(), screenshot, new DiscordClient.Listener()
            {
                @Override public void onSending() { }
                @Override public void onResult(DeliveryStatus status, String detail, String id)
                {
                    assertFalse(detail.contains("abcdefghijkl"));
                    if (status == DeliveryStatus.SENT) { assertEquals("123456789", id); }
                    done.complete(status);
                }
            });
        return done;
    }

    @Test public void embedsAndScreenshotUseOneAcknowledgedMultipartRequest() throws Exception
    {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        setup(chain -> {
            requests.incrementAndGet();
            assertEquals("true", chain.request().url().queryParameter("wait"));
            Buffer buffer = new Buffer(); chain.request().body().writeTo(buffer); body.set(buffer.readUtf8());
            return response(chain.request(), 200, "{\"id\":\"123456789\"}");
        });
        assertEquals(DeliveryStatus.SENT, submit(new byte[]{1, 2, 3}).get(3, TimeUnit.SECONDS));
        assertEquals(1, requests.get());
        assertTrue(body.get().contains("payload_json"));
        assertTrue(body.get().contains("files[0]"));
        assertTrue(body.get().contains("attachment://loot.png"));
    }

    @Test public void rateLimitRetriesOnlyAfterExplicitRetryDelay() throws Exception
    {
        AtomicInteger requests = new AtomicInteger();
        setup(chain -> requests.incrementAndGet() == 1
            ? response(chain.request(), 429, "{\"retry_after\":0.01}")
            : response(chain.request(), 200, "{\"id\":\"123456789\"}"));
        assertEquals(DeliveryStatus.SENT, submit(null).get(3, TimeUnit.SECONDS));
        assertEquals(2, requests.get());
    }

    @Test public void serverErrorIsUncertainWithoutBlindRetry() throws Exception
    {
        AtomicInteger requests = new AtomicInteger();
        setup(chain -> { requests.incrementAndGet(); return response(chain.request(), 503, "{}"); });
        assertEquals(DeliveryStatus.UNCERTAIN, submit(null).get(3, TimeUnit.SECONDS));
        assertEquals(1, requests.get());
    }

    @Test public void ioErrorIsUncertainWithoutBlindRetry() throws Exception
    {
        AtomicInteger requests = new AtomicInteger();
        setup(chain -> { requests.incrementAndGet(); throw new IOException("fake socket error"); });
        assertEquals(DeliveryStatus.UNCERTAIN, submit(null).get(3, TimeUnit.SECONDS));
        assertEquals(1, requests.get());
    }

    @Test public void noAcknowledgementIsUncertain() throws Exception
    {
        setup(chain -> response(chain.request(), 204, ""));
        assertEquals(DeliveryStatus.UNCERTAIN, submit(null).get(3, TimeUnit.SECONDS));
    }

    @Test public void invalidWebhookIsDefiniteFailure() throws Exception
    {
        setup(chain -> response(chain.request(), 404, "{\"message\":\"Unknown Webhook\"}"));
        assertEquals(DeliveryStatus.FAILED, submit(null).get(3, TimeUnit.SECONDS));
    }

    @Test public void cancelInvalidatesQueuedRequestsAndPreventsDuplicateInFlight() throws Exception
    {
        CompletableFuture<Void> entered = new CompletableFuture<>(), release = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        setup(chain -> {
            requests.incrementAndGet(); entered.complete(null);
            try { release.get(3, TimeUnit.SECONDS); }
            catch (Exception e) { throw new IOException("test interrupted"); }
            return response(chain.request(), 200, "{\"id\":\"123456789\"}");
        });
        CompletableFuture<DeliveryStatus> first = submit(null);
        entered.get(3, TimeUnit.SECONDS);
        CompletableFuture<DeliveryStatus> second = submit(null);
        client.cancelAll();
        assertEquals(DeliveryStatus.UNCERTAIN, first.get(3, TimeUnit.SECONDS));
        assertEquals(DeliveryStatus.FAILED, second.get(3, TimeUnit.SECONDS));
        release.complete(null);
        assertEquals(1, requests.get());
    }
}

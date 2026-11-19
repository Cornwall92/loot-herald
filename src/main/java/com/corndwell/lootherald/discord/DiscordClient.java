package com.corndwell.lootherald.discord;

import com.corndwell.lootherald.history.DeliveryStatus;
import com.corndwell.lootherald.model.LootDrop;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Authenticator;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Serial per-endpoint dispatcher. Only explicit rate-limit rejections are retried. */
public final class DiscordClient
{
    public interface Listener
    {
        void onSending();
        void onResult(DeliveryStatus status, String safeDetail, String messageId);
    }

    private static final int MAX_PENDING = 100;
    private static final long MAX_PENDING_BYTES = 64L * 1024 * 1024;
    private static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType PNG = MediaType.parse("image/png");
    private final Object lock = new Object();
    private final OkHttpClient http;
    private final Gson gson;
    private final ScheduledExecutorService executor;
    private final DiscordFormatter formatter = new DiscordFormatter();
    private final Map<String, Endpoint> endpoints = new HashMap<>();
    private int pending;
    private long pendingBytes, generation;

    public DiscordClient(OkHttpClient http, Gson gson, ScheduledExecutorService executor)
    {
        this.http = Objects.requireNonNull(http).newBuilder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .addNetworkInterceptor(chain -> {
                Response response = chain.proceed(chain.request());
                // OkHttp may otherwise retry a 503 with Retry-After: 0 even with connection retries disabled.
                return response.code() == 503 ? response.newBuilder().header("Retry-After", "1").build() : response;
            })
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build();
        this.gson = Objects.requireNonNull(gson);
        this.executor = Objects.requireNonNull(executor);
    }

    public void submit(Destination destination, LootDrop drop, List<Integer> indices,
        EmbedOptions options, byte[] screenshot, Listener listener)
    {
        Objects.requireNonNull(listener);
        if (screenshot != null && screenshot.length > MAX_SCREENSHOT_BYTES)
        {
            result(listener, DeliveryStatus.FAILED, "Screenshot exceeds the upload limit. Retry without a screenshot.", null);
            return;
        }
        final String payload;
        try { payload = gson.toJson(formatter.format(drop, indices, options, screenshot != null)); }
        catch (RuntimeException e)
        {
            result(listener, DeliveryStatus.FAILED, "Unable to prepare the selected reward.", null);
            return;
        }
        Job job;
        String rejected = null;
        synchronized (lock)
        {
            long bytes = screenshot == null ? 0 : screenshot.length;
            if (destination == null) { rejected = "No destination selected."; }
            else if (pending >= MAX_PENDING || pendingBytes + bytes > MAX_PENDING_BYTES) { rejected = "Delivery queue is full. Try again after pending messages finish."; }
            if (rejected == null)
            {
                job = new Job(generation, destination, payload, screenshot == null ? null : screenshot.clone(), listener);
                endpoints.computeIfAbsent(job.queueId, key -> new Endpoint()).queue.addLast(job);
                pending++;
                pendingBytes += bytes;
            }
            else { job = null; }
        }
        if (rejected != null) { result(listener, DeliveryStatus.FAILED, rejected, null); }
        else { dispatch(job.queueId); }
    }

    /** Cancels queued/retry work and invalidates all callbacks from this generation. */
    public void cancelAll()
    {
        List<Job> cancelled = new ArrayList<>();
        synchronized (lock)
        {
            generation++;
            for (Endpoint endpoint : endpoints.values())
            {
                cancelled.addAll(endpoint.queue);
                if (endpoint.current != null) { cancelled.add(endpoint.current); }
            }
            endpoints.clear();
            pending = 0;
            pendingBytes = 0;
            for (Job job : cancelled)
            {
                if (job.retry != null) { job.retry.cancel(false); }
                if (job.call != null) { job.call.cancel(); }
            }
        }
        for (Job job : cancelled)
        {
            boolean uncertain = job.inFlight;
            result(job.listener, uncertain ? DeliveryStatus.UNCERTAIN : DeliveryStatus.FAILED,
                uncertain ? "Cancelled during delivery. Check Discord before posting again." : "Queued delivery cancelled.", null);
        }
    }

    private void dispatch(String endpointId)
    {
        try { executor.execute(() -> startNext(endpointId)); }
        catch (RejectedExecutionException e) { rejectEndpoint(endpointId); }
    }

    private void startNext(String endpointId)
    {
        Job job;
        synchronized (lock)
        {
            Endpoint endpoint = endpoints.get(endpointId);
            if (endpoint == null || endpoint.current != null) { return; }
            job = endpoint.queue.pollFirst();
            if (job == null) { endpoints.remove(endpointId); return; }
            endpoint.current = job;
        }
        try { job.listener.onSending(); } catch (RuntimeException ignored) { /* An observer must not stall other destinations. */ }
        send(job);
    }

    private void send(Job job)
    {
        final Call call;
        try
        {
            HttpUrl base = HttpUrl.parse(job.destination.getUrl());
            if (base == null) { throw new IllegalArgumentException(); }
            HttpUrl url = base.newBuilder().setQueryParameter("wait", "true").build();
            RequestBody body;
            if (job.screenshot == null) { body = RequestBody.create(JSON, job.payload); }
            else
            {
                body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("payload_json", job.payload)
                    .addFormDataPart("files[0]", "loot.png", RequestBody.create(PNG, job.screenshot)).build();
            }
            Request request = new Request.Builder().url(url).post(body).build();
            call = http.newCall(request);
        }
        catch (RuntimeException e)
        {
            finish(job, DeliveryStatus.FAILED, "Unable to prepare the Discord request.", null);
            return;
        }
        synchronized (lock)
        {
            if (!active(job)) { call.cancel(); return; }
            job.call = call;
            job.inFlight = true;
        }
        try { call.enqueue(new Callback()
        {
            @Override public void onFailure(Call failed, IOException exception)
            {
                finish(job, DeliveryStatus.UNCERTAIN, "Delivery could not be confirmed. Check Discord before posting again.", null);
            }

            @Override public void onResponse(Call completed, Response response)
            {
                try (Response ignored = response)
                {
                    int code = response.code();
                    if (code == 429)
                    {
                        Long delay = retryDelay(response);
                        retry(job, delay);
                    }
                    else if (code >= 200 && code < 300)
                    {
                        String messageId = messageId(response.body());
                        if (messageId == null)
                        {
                            finish(job, DeliveryStatus.UNCERTAIN, "Discord accepted the request without a message ID. Check Discord before posting again.", null);
                        }
                        else { finish(job, DeliveryStatus.SENT, "Posted to Discord.", messageId); }
                    }
                    else if (code >= 500 || code == 408)
                    {
                        finish(job, DeliveryStatus.UNCERTAIN, "Discord returned an uncertain response (HTTP " + code + "). Check Discord before posting again.", null);
                    }
                    else
                    {
                        finish(job, DeliveryStatus.FAILED, failureMessage(code), null);
                    }
                }
                catch (IOException | RuntimeException e)
                {
                    finish(job, DeliveryStatus.UNCERTAIN, "Discord's response could not be confirmed. Check Discord before posting again.", null);
                }
            }
        }); }
        catch (RuntimeException e)
        {
            finish(job, DeliveryStatus.UNCERTAIN, "Delivery could not be confirmed. Check Discord before posting again.", null);
        }
    }

    private void retry(Job job, Long delayMillis)
    {
        synchronized (lock)
        {
            if (!active(job)) { return; }
            job.inFlight = false;
            job.call = null;
            if (delayMillis != null && delayMillis <= 300_000 && job.retries < MAX_RATE_LIMIT_RETRIES)
            {
                job.retries++;
                try { job.retry = executor.schedule(() -> send(job), Math.max(100, delayMillis), TimeUnit.MILLISECONDS); return; }
                catch (RejectedExecutionException ignored) { /* Fail the rate-limited request below. */ }
            }
        }
        finish(job, DeliveryStatus.FAILED, "Discord rate limit reached. Try again later.", null);
    }

    private Long retryDelay(Response response)
    {
        String header = response.header("Retry-After");
        if (header != null)
        {
            Long delay = seconds(header);
            if (delay != null) { return delay; }
        }
        try
        {
            JsonObject body = gson.fromJson(readSmall(response.body()), JsonObject.class);
            return body != null && body.has("retry_after") ? seconds(body.get("retry_after").getAsString()) : null;
        }
        catch (IOException | RuntimeException e) { return null; }
    }

    private static Long seconds(String value)
    {
        try
        {
            double seconds = Double.parseDouble(value);
            return Double.isFinite(seconds) && seconds >= 0 && seconds <= 300 ? (long) Math.ceil(seconds * 1000) : null;
        }
        catch (NumberFormatException e) { return null; }
    }

    private String messageId(ResponseBody response) throws IOException
    {
        try
        {
            JsonObject body = gson.fromJson(readSmall(response), JsonObject.class);
            if (body != null && body.has("id"))
            {
                String id = body.get("id").getAsString();
                if (id.matches("[0-9]{1,25}")) { return id; }
            }
            return null;
        }
        catch (RuntimeException e) { return null; }
    }

    private static String readSmall(ResponseBody body) throws IOException
    {
        if (body == null) { return ""; }
        try (Reader reader = body.charStream())
        {
            char[] buffer = new char[2048];
            StringBuilder text = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) != -1)
            {
                if (text.length() + count > 65536) { throw new IOException("Response too large"); }
                text.append(buffer, 0, count);
            }
            return text.toString();
        }
    }

    private boolean active(Job job)
    {
        Endpoint endpoint = endpoints.get(job.queueId);
        return job.generation == generation && endpoint != null && endpoint.current == job;
    }

    private void finish(Job job, DeliveryStatus status, String detail, String messageId)
    {
        synchronized (lock)
        {
            if (!active(job)) { return; }
            endpoints.get(job.queueId).current = null;
            pending--;
            pendingBytes -= job.screenshot == null ? 0 : job.screenshot.length;
            if (job.retry != null) { job.retry.cancel(false); }
        }
        result(job.listener, status, detail, messageId);
        dispatch(job.queueId);
    }

    private void rejectEndpoint(String endpointId)
    {
        List<Job> rejected = new ArrayList<>();
        synchronized (lock)
        {
            Endpoint endpoint = endpoints.get(endpointId);
            if (endpoint == null || endpoint.current != null) { return; }
            rejected.addAll(endpoint.queue);
            endpoints.remove(endpointId);
            for (Job job : rejected) { pending--; pendingBytes -= job.screenshot == null ? 0 : job.screenshot.length; }
        }
        for (Job job : rejected) { result(job.listener, DeliveryStatus.FAILED, "Delivery service is stopped.", null); }
    }

    private static String failureMessage(int code)
    {
        if (code == 401 || code == 403 || code == 404) { return "Discord rejected this webhook (HTTP " + code + "). Check its configuration."; }
        if (code == 413) { return "Discord rejected the upload size. Retry without a screenshot."; }
        if (code >= 300 && code < 400) { return "Webhook redirect refused. Use a direct Discord webhook URL."; }
        return "Discord rejected the message (HTTP " + code + ").";
    }

    private static void result(Listener listener, DeliveryStatus status, String detail, String messageId)
    {
        try { listener.onResult(status, detail, messageId); } catch (RuntimeException ignored) { /* Protect dispatcher liveness. */ }
    }

    private static final class Endpoint
    {
        private final ArrayDeque<Job> queue = new ArrayDeque<>();
        private Job current;
    }

    private static final class Job
    {
        private final long generation;
        private final Destination destination;
        private final String queueId;
        private final String payload;
        private final byte[] screenshot;
        private final Listener listener;
        private int retries;
        private boolean inFlight;
        private Call call;
        private ScheduledFuture<?> retry;
        private Job(long generation, Destination destination, String payload, byte[] screenshot, Listener listener)
        {
            this.generation = generation; this.destination = destination; this.payload = payload;
            this.queueId = destination.queueId();
            this.screenshot = screenshot; this.listener = listener;
        }
    }
}

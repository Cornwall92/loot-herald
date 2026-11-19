package com.corndwell.lootherald.capture;

import net.runelite.client.ui.DrawManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Bounded next-frame capture. Masks are applied before any image is retained. */
public final class ScreenshotService {
    private final DrawManager drawManager;
    private final ScheduledExecutorService executor;
    private final java.util.Set<CompletableFuture<byte[]>> outstanding =
            ConcurrentHashMap.newKeySet();
    private final AtomicInteger pending = new AtomicInteger();

    public ScreenshotService(DrawManager drawManager, ScheduledExecutorService executor) {
        this.drawManager = drawManager;
        this.executor = executor;
    }

    public void cancelAll() {
        for (CompletableFuture<byte[]> future : outstanding) future.complete(null);
    }

    public CompletableFuture<byte[]> capture(List<Rectangle> masks) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        if (pending.incrementAndGet() > 4) {
            pending.decrementAndGet();
            result.complete(null);
            return result;
        }
        outstanding.add(result);
        final List<Rectangle> rectangles = List.copyOf(masks);
        final ScheduledFuture<?> expiry =
                executor.schedule(() -> result.complete(null), 2, TimeUnit.SECONDS);
        result.whenComplete(
                (v, e) -> {
                    pending.decrementAndGet();
                    outstanding.remove(result);
                    expiry.cancel(false);
                });
        drawManager.requestNextFrameListener(
                frame -> {
                    if (result.isDone()) return;
                    try {
                        int width = frame.getWidth(null), height = frame.getHeight(null);
                        if (width <= 0 || height <= 0) {
                            result.complete(null);
                            return;
                        }
                        double scale = Math.min(1d, Math.min(1920d / width, 1080d / height));
                        BufferedImage copy =
                                new BufferedImage(
                                        Math.max(1, (int) (width * scale)),
                                        Math.max(1, (int) (height * scale)),
                                        BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = copy.createGraphics();
                        try {
                            graphics.setRenderingHint(
                                    RenderingHints.KEY_INTERPOLATION,
                                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            graphics.drawImage(
                                    frame, 0, 0, copy.getWidth(), copy.getHeight(), null);
                            graphics.setColor(Color.BLACK);
                            for (Rectangle r : rectangles)
                                graphics.fillRect(
                                        (int) Math.floor(r.x * scale),
                                        (int) Math.floor(r.y * scale),
                                        (int) Math.ceil(r.width * scale) + 2,
                                        (int) Math.ceil(r.height * scale) + 2);
                        } finally {
                            graphics.dispose();
                        }
                        executor.execute(
                                () -> {
                                    if (result.isDone()) return;
                                    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                            MemoryCacheImageOutputStream output =
                                                    new MemoryCacheImageOutputStream(bytes)) {
                                        ImageIO.write(copy, "png", output);
                                        output.flush();
                                        result.complete(
                                                bytes.size() <= 8 * 1024 * 1024
                                                        ? bytes.toByteArray()
                                                        : null);
                                    } catch (Exception error) {
                                        result.complete(null);
                                    }
                                });
                    } catch (RuntimeException error) {
                        result.complete(null);
                    }
                });
        return result;
    }
}

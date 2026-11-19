package com.corndwell.lootherald.capture;

import static org.junit.Assert.*;

import net.runelite.client.ui.DrawManager;

import org.junit.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

public class ScreenshotServiceTest {
    private ScheduledExecutorService executor;
    private DrawManager draw;
    private ScreenshotService service;

    @Before
    public void before() {
        executor = Executors.newSingleThreadScheduledExecutor();
        draw = new DrawManager();
        service = new ScreenshotService(draw, executor);
    }

    @After
    public void after() {
        service.cancelAll();
        executor.shutdownNow();
    }

    @Test
    public void masksBeforeRetainingAndCopiesTheOriginalFrame() throws Exception {
        BufferedImage frame = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = frame.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 80, 60);
        graphics.dispose();
        CompletableFuture<byte[]> result = service.capture(List.of(new Rectangle(0, 40, 30, 20)));
        draw.processDrawComplete(() -> frame);
        frame.setRGB(70, 5, Color.RED.getRGB());
        BufferedImage copy =
                ImageIO.read(new ByteArrayInputStream(result.get(3, TimeUnit.SECONDS)));
        assertEquals(Color.BLACK.getRGB(), copy.getRGB(10, 50));
        assertEquals(Color.WHITE.getRGB(), copy.getRGB(70, 5));
    }

    @Test
    public void cancellationInvalidatesNextFrameAndReleasesCapacity() throws Exception {
        CompletableFuture<byte[]> pending = service.capture(List.of());
        service.cancelAll();
        assertNull(pending.get(1, TimeUnit.SECONDS));
        CompletableFuture<byte[]> next = service.capture(List.of());
        draw.processDrawComplete(() -> new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        assertNotNull(next.get(3, TimeUnit.SECONDS));
        assertNull(pending.get());
    }

    @Test
    public void boundsConcurrentCaptureWork() throws Exception {
        for (int i = 0; i < 4; i++) assertFalse(service.capture(List.of()).isDone());
        assertNull(service.capture(List.of()).get(1, TimeUnit.SECONDS));
        service.cancelAll();
    }
}

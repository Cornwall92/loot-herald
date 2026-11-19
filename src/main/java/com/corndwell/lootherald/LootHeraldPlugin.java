package com.corndwell.lootherald;

import com.corndwell.lootherald.capture.ScreenshotService;
import com.corndwell.lootherald.detection.*;
import com.corndwell.lootherald.discord.*;
import com.corndwell.lootherald.filter.*;
import com.corndwell.lootherald.history.*;
import com.corndwell.lootherald.model.*;
import com.corndwell.lootherald.rarity.*;
import com.corndwell.lootherald.ui.DestinationEditor;
import com.corndwell.lootherald.ui.HistoryPanel;
import com.corndwell.lootherald.ui.MessagePreview;
import com.google.gson.Gson;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.*;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import okhttp3.OkHttpClient;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import javax.inject.Inject;
import javax.swing.*;

@Slf4j
@PluginDescriptor(
        name = "Loot Herald — for Discord",
        description = "Loot and pet notifications with session history and safe manual posting",
        tags = {"discord", "loot", "drops", "pets", "raids"})
public class LootHeraldPlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ItemManager itemManager;
    @Inject private ConfigManager configManager;
    @Inject private LootHeraldConfig config;
    @Inject private ClientToolbar toolbar;
    @Inject private DrawManager drawManager;
    @Inject private PluginManager pluginManager;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private ScheduledExecutorService executor;
    private final HistoryStore history = new HistoryStore();
    private final RewardGate rewards = new RewardGate();
    private final PetDetector pets = new PetDetector();
    private final LootFilter filter = new LootFilter();
    private final java.util.Deque<PendingLoot> pendingLoot = new ArrayDeque<>();
    private final Map<Integer, CapturedImage> petImages = new HashMap<>();
    private final Map<Integer, Instant> petTimes = new HashMap<>();
    private final Map<String, Integer> collectionSignals = new HashMap<>();
    private volatile RarityService rarity;
    private DiscordClient discord;
    private ScreenshotService screenshots;
    private volatile HistoryPanel panel;
    private NavigationButton navigation;
    private volatile boolean active;
    private volatile boolean ready;
    private volatile long lifecycle;
    private String character = "";
    private volatile List<Destination> destinations = List.of();
    private DestinationRoutes routes;
    private volatile String configurationError = "";
    private String collectionPopup = "";
    private boolean collectionPopupArmed;
    private Boolean previousMaskChat;
    private volatile long screenshotEpoch;
    private volatile long deliveryEpoch;
    private String previousWebhooks, previousRoutes;
    private boolean previousPostingEnabled;
    private static final Pattern COLLECTION =
            Pattern.compile("^New item added to your collection log: (.+)$");

    @Provides
    LootHeraldConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(LootHeraldConfig.class);
    }

    @Override
    protected void startUp() {
        active = true;
        ready = false;
        final long started = ++lifecycle;
        discord = new DiscordClient(http, gson, executor);
        screenshots = new ScreenshotService(drawManager, executor);
        history.clear();
        previousMaskChat = null;
        previousWebhooks = null;
        previousRoutes = null;
        previousPostingEnabled = false;
        collectionPopup = "";
        executor.execute(
                () -> {
                    try {
                        RarityService loaded = new RarityService(gson);
                        if (active && lifecycle == started) {
                            rarity = loaded;
                            refresh();
                        }
                    } catch (RuntimeException error) {
                        log.debug("Unable to load bundled rarity data", error);
                    }
                });
        SwingUtilities.invokeLater(
                () -> {
                    if (!active || lifecycle != started) return;
                    panel =
                            new HistoryPanel(
                                    history,
                                    new HistoryPanel.Actions() {
                                        public void post(
                                                String id,
                                                Integer index,
                                                String destination,
                                                boolean repeat) {
                                            clientThread.invokeLater(
                                                    () ->
                                                            manualPost(
                                                                    id,
                                                                    index,
                                                                    destination,
                                                                    repeat));
                                        }

                                        public void clearSession() {
                                            clientThread.invokeLater(
                                                    () -> LootHeraldPlugin.this.clearSession());
                                        }

                                        public void setPaused(boolean paused) {
                                            configManager.setConfiguration(
                                                    LootHeraldConfig.GROUP, "automatic", !paused);
                                        }

                                        public void testDestination(String destination) {
                                            clientThread.invokeLater(() -> sendTest(destination));
                                        }

                                        public void preview(String id, Integer index) {
                                            clientThread.invokeLater(() -> previewDrop(id, index));
                                        }

                                        public void configureDestinations() {
                                            DestinationEditor.show(
                                                    panel,
                                                    config.webhooks(),
                                                    value ->
                                                            configManager.setConfiguration(
                                                                    LootHeraldConfig.GROUP,
                                                                    "webhooks",
                                                                    value));
                                        }
                                    });
                    BufferedImage icon =
                            ImageUtil.loadImageResource(LootHeraldPlugin.class, "icon.png");
                    navigation =
                            NavigationButton.builder()
                                    .tooltip("Loot Herald")
                                    .icon(ImageUtil.resizeImage(icon, 16, 24))
                                    .priority(7)
                                    .panel(panel)
                                    .build();
                    toolbar.addNavigation(navigation);
                    updatePanelConfiguration();
                });
        clientThread.invokeLater(
                () -> {
                    if (active && lifecycle == started) {
                        resetTransientState();
                        refreshConfiguration();
                        observeCharacter();
                        ready = true;
                        updateDiagnostic();
                    }
                });
    }

    @Override
    protected void shutDown() {
        active = false;
        ready = false;
        final long stopped = ++lifecycle;
        ++deliveryEpoch;
        if (discord != null) discord.cancelAll();
        if (screenshots != null) screenshots.cancelAll();
        ++screenshotEpoch;
        history.clear();
        rarity = null;
        clientThread.invokeLater(
                () -> {
                    if (lifecycle == stopped) resetTransientState();
                });
        NavigationButton old = navigation;
        navigation = null;
        panel = null;
        SwingUtilities.invokeLater(
                () -> {
                    if (old != null) toolbar.removeNavigation(old);
                });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (LootHeraldConfig.GROUP.equals(event.getGroup()))
            clientThread.invokeLater(
                    () -> {
                        if (active) refreshConfiguration();
                    });
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (active) clientThread.invokeLater(this::updateDiagnostic);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (!active || !ready) return;
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            clearSession();
            character = "";
        } else if (event.getGameState() == GameState.LOGGED_IN) observeCharacter();
    }

    private void observeCharacter() {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN
                || player == null
                || player.getName() == null) return;
        String name = Text.removeTags(player.getName());
        if (!character.isEmpty() && !character.equalsIgnoreCase(name)) clearSession();
        character = name;
    }

    private void clearSession() {
        ++deliveryEpoch;
        ++screenshotEpoch;
        history.clear();
        if (discord != null) discord.cancelAll();
        if (screenshots != null) screenshots.cancelAll();
        pendingLoot.clear();
        petImages.clear();
        petTimes.clear();
        collectionSignals.clear();
        pets.clear();
        rewards.clear();
        collectionPopup = "";
        collectionPopupArmed = false;
        refresh();
    }

    private void refreshConfiguration() {
        if (previousMaskChat != null && previousMaskChat != config.maskChat()) {
            ++screenshotEpoch;
            ++deliveryEpoch;
            screenshots.cancelAll();
            discord.cancelAll();
            petImages.clear();
            for (HistoryStore.Entry entry : history.entries()) {
                history.screenshot(entry.getDrop().getId(), null);
                history.screenshotState(
                        entry.getDrop().getId(), "Expired (privacy settings changed)");
            }
        }
        previousMaskChat = config.maskChat();
        if ((previousWebhooks != null && !previousWebhooks.equals(config.webhooks()))
                || (previousRoutes != null && !previousRoutes.equals(config.routes()))
                || (previousPostingEnabled && !config.postingEnabled())) {
            ++deliveryEpoch;
            discord.cancelAll();
        }
        previousWebhooks = config.webhooks();
        previousRoutes = config.routes();
        previousPostingEnabled = config.postingEnabled();
        try {
            destinations = Destination.parse(config.webhooks());
            routes = new DestinationRoutes(config.routes(), destinations);
            configurationError = "";
        } catch (IllegalArgumentException error) {
            destinations = List.of();
            routes = null;
            configurationError = error.getMessage();
        }
        if (!config.postingEnabled() && discord != null) discord.cancelAll();
        updatePanelConfiguration();
        updateDiagnostic();
    }

    private void updatePanelConfiguration() {
        HistoryPanel p = panel;
        if (p != null) {
            p.setDestinations(destinations);
            p.setPaused(!config.automatic());
            p.refresh();
        }
    }

    private void updateDiagnostic() {
        if (!active) return;
        boolean tracker =
                pluginManager.getPlugins().stream()
                        .anyMatch(
                                p ->
                                        p instanceof LootTrackerPlugin
                                                && pluginManager.isPluginActive(p));
        boolean other =
                pluginManager.getPlugins().stream()
                        .anyMatch(
                                p ->
                                        p != this
                                                && pluginManager.isPluginActive(p)
                                                && (p.getClass()
                                                                .getSimpleName()
                                                                .equals("DinkPlugin")
                                                        || p.getClass()
                                                                .getSimpleName()
                                                                .contains("DiscordRareDrop")));
        String message =
                !configurationError.isEmpty()
                        ? configurationError
                        : !tracker
                                ? "Enable RuneLite's built-in Loot Tracker to record loot. Pet chat"
                                        + " detection remains available."
                                : !config.postingEnabled()
                                        ? "History is active. Enable Discord posting in settings to"
                                                + " send messages."
                                        : destinations.isEmpty()
                                                ? "Add a Discord webhook destination in settings."
                                                : "Tracking this session · "
                                                        + destinations.size()
                                                        + " destination(s)";
        if (other)
            message +=
                    " Other notification plugins are active; disable their loot/pet posts to avoid"
                            + " duplicate messages.";
        if (rarity == null) message += " Rarity data is loading; missing rates stay unknown.";
        HistoryPanel p = panel;
        if (p != null) p.setDiagnostic(message);
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (!active || !ready) return;
        observeCharacter();
        if (character.isEmpty()) return;
        int tick = client.getTickCount();
        Map<Integer, Long> quantities = new LinkedHashMap<>();
        for (ItemStack stack : event.getItems())
            if (stack.getQuantity() > 0)
                quantities.merge(
                        itemManager.canonicalize(stack.getId()),
                        (long) stack.getQuantity(),
                        LootFilter::add);
        List<DropItem> all = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : quantities.entrySet()) {
            int id = entry.getKey();
            ItemComposition item = itemManager.getItemComposition(id);
            String name = item.getName();
            long price = itemManager.getItemPrice(id);
            if (price <= 0) price = -1;
            long alch = item.getHaPrice();
            Rarity r =
                    rarity == null
                            ? Rarity.unknown()
                            : rarity.lookup(
                                    event.getName(), event.getType().name(), id, entry.getValue());
            if (event.getAmount() > 1)
                r =
                        new Rarity(
                                null,
                                "Batched reward; per-acquisition odds unavailable",
                                r.isUnique());
            all.add(new DropItem(id, name, entry.getValue(), price, Math.max(0, alch), r));
        }
        if (all.isEmpty()) return;
        boolean hasPet = all.stream().anyMatch(i -> pets.isPet(i.getId(), i.getName()));

        Map<String, String> details = new LinkedHashMap<>();
        if (event.getMetadata() instanceof int[]
                && event.getName().startsWith("Tombs of Amascut")) {
            int[] metadata = ((int[]) event.getMetadata()).clone();
            if (metadata.length > 0 && metadata[0] >= 0)
                details.put("Raid level", Integer.toString(metadata[0]));
            if (metadata.length > 1 && metadata[1] > 0)
                details.put("Team size", Integer.toString(metadata[1]));
        }
        LootDrop drop =
                baseDrop(event.getName(), event.getType().name(), all)
                        .combatLevel(event.getCombatLevel())
                        .batchCount(event.getAmount())
                        .details(details)
                        .build();
        boolean qualifies = hasPet || !filter.evaluate(drop, config).indices.isEmpty();
        CapturedImage image =
                (config.captureFiltered()
                                || (qualifies
                                        && (config.automaticScreenshots()
                                                || config.manualScreenshots())))
                        ? capture()
                        : noImage();
        pendingLoot.addLast(new PendingLoot(drop, tick, image));
    }

    private LootDrop.LootDropBuilder baseDrop(String rawSource, String type, List<DropItem> items) {
        return LootDrop.builder()
                .id(UUID.randomUUID().toString())
                .session(history.getSession())
                .received(Instant.now())
                .player(character)
                .accountType(client.getAccountType() == null ? "" : client.getAccountType().name())
                .source(SourceNames.normalize(rawSource))
                .rawSource(rawSource)
                .type(type)
                .combatLevel(0)
                .batchCount(1)
                .pet(false)
                .duplicatePet(false)
                .collectionLog(false)
                .items(items)
                .details(Map.of());
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!active || !ready) return;
        observeCharacter();
        if (character.isEmpty()) return;
        if ("RuneLite".equals(event.getName()) || "RuneLite".equals(event.getSender())) return;
        String type = event.getType().name(), text = Text.removeTags(event.getMessage());
        int tick = client.getTickCount();
        pets.onChat(type, text, character, tick);
        if (PetDetector.isAcquisitionMessage(type, text)) capturePet(tick);
        if (event.getType() == ChatMessageType.GAMEMESSAGE
                || event.getType() == ChatMessageType.SPAM) {
            rewards.onChat(text, tick);
            Matcher match = COLLECTION.matcher(text);
            if (match.matches()) recordCollection(match.group(1), tick);
        }
    }

    private void recordCollection(String name, int tick) {
        String cleaned = name.replaceAll("[.!]+$", "").trim();
        collectionSignals.put(cleaned.toLowerCase(Locale.ROOT), tick);
        pets.onCollection(cleaned, tick);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (!active || !ready) return;
        if (event.getScriptId() == ScriptID.NOTIFICATION_START) {
            collectionPopup = "";
            collectionPopupArmed = true;
            return;
        }
        if (event.getScriptId() != ScriptID.NOTIFICATION_DELAY || !collectionPopupArmed) return;
        collectionPopupArmed = false;
        String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE),
                body = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
        if (title == null || body == null) return;
        if (Text.removeTags(title).equalsIgnoreCase("Collection log")) {
            String name = Text.removeTags(body).replaceFirst("(?i)^New item:?\\s*", "");
            if (!name.equals(collectionPopup)) {
                collectionPopup = name;
                recordCollection(name, client.getTickCount());
            }
        } else collectionPopup = "";
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!active || !ready) return;
        observeCharacter();
        int tick = client.getTickCount();
        while (!pendingLoot.isEmpty() && pendingLoot.peekFirst().tick < tick) {
            PendingLoot pending = pendingLoot.removeFirst();
            if (pending.drop.getSession() != history.getSession()) continue;
            RewardGate.Decision decision =
                    rewards.decide(
                            pending.drop.getRawSource(),
                            pending.drop.getType(),
                            pending.drop.getItems(),
                            pending.tick);
            if (decision == RewardGate.Decision.DUPLICATE) continue;
            LootDrop observed = pending.drop;
            if (decision == RewardGate.Decision.ACCEPT) {
                pets.onLoot(observed.getItems(), observed.getRawSource(), pending.tick);
                for (DropItem item : observed.getItems()) {
                    Integer collectionTick =
                            collectionSignals.get(item.getName().toLowerCase(Locale.ROOT));
                    if (collectionTick != null && Math.abs(collectionTick - pending.tick) <= 2)
                        pets.onCollection(item.getName(), Math.max(collectionTick, pending.tick));
                }
                List<DropItem> normal = new ArrayList<>();
                for (DropItem item : observed.getItems()) {
                    if (pets.isPet(item.getId(), item.getName())) {
                        petImages.putIfAbsent(pending.tick, pending.image);
                        petTimes.putIfAbsent(pending.tick, observed.getReceived());
                    } else normal.add(item);
                }
                if (normal.isEmpty()) continue;
                observed = observed.toBuilder().items(normal).build();
            } else if (observed.getItems().stream()
                    .allMatch(i -> pets.isPet(i.getId(), i.getName()))) {
                observed = observed.toBuilder().pet(true).build();
            }
            boolean collection =
                    observed.getItems().stream()
                            .anyMatch(
                                    i -> {
                                        Integer t =
                                                collectionSignals.get(
                                                        i.getName().toLowerCase(Locale.ROOT));
                                        return t != null && Math.abs(t - pending.tick) <= 2;
                                    });
            record(
                    observed.toBuilder().collectionLog(collection).build(),
                    pending.image,
                    decision == RewardGate.Decision.REVIEW ? RewardGate.REVIEW_REASON : null);
        }
        for (PetDetector.PetAward award : pets.drain(tick)) {
            DropItem item =
                    new DropItem(award.getItemId(), award.getName(), 1, -1, -1, Rarity.unknown());
            LootDrop drop =
                    baseDrop(award.getSource(), "PET", List.of(item))
                            .received(petTimes.getOrDefault(award.getTick(), Instant.now()))
                            .pet(true)
                            .duplicatePet(award.isDuplicate())
                            .collectionLog(award.isCollectionLog())
                            .build();
            CapturedImage image = petImages.get(award.getTick());
            record(drop, image == null ? noImage() : image, null);
        }
        petImages.keySet().removeIf(t -> tick - t > 12);
        petTimes.keySet().removeIf(t -> tick - t > 12);
        collectionSignals.entrySet().removeIf(e -> tick - e.getValue() > 8);
        if (tick % 10 == 0) updateDiagnostic();
    }

    private void capturePet(int tick) {
        petTimes.putIfAbsent(tick, Instant.now());
        if (config.captureFiltered() || config.automaticScreenshots() || config.manualScreenshots())
            petImages.computeIfAbsent(tick, t -> capture());
    }

    private CapturedImage capture() {
        List<Rectangle> masks = new ArrayList<>();
        if (config.maskChat()) {
            Widget chat = client.getWidget(InterfaceID.Chatbox.CHATAREA);
            if (chat == null
                    || chat.getBounds() == null
                    || chat.getBounds().width <= 0
                    || chat.getBounds().height <= 0) return noImage();
            masks.add(new Rectangle(chat.getBounds()));
            for (int component :
                    new int[] {InterfaceID.Chatbox.INPUT, InterfaceID.PmChat.CONTAINER}) {
                Widget widget = client.getWidget(component);
                if (widget != null && !widget.isHidden() && widget.getBounds() != null)
                    masks.add(new Rectangle(widget.getBounds()));
            }
            // If the layout cannot be resolved, omit the image instead of risking an unmasked
            // upload.
            if (masks.isEmpty()) return noImage();
        }
        long epoch = screenshotEpoch;
        return new CapturedImage(epoch, screenshots.capture(masks));
    }

    private void record(LootDrop drop, CapturedImage image, String heldReason) {
        if (drop.getSession() != history.getSession() || !active) return;
        LootFilter.Result result = filter.evaluate(drop, config);
        String reason =
                heldReason != null
                        ? heldReason
                        : !config.postingEnabled()
                                ? "Discord posting disabled"
                                : !config.automatic()
                                        ? "Automatic posting paused"
                                        : !allowedWorld()
                                                ? "Automatic posting disabled on this world"
                                                : result.reason;
        history.add(drop, reason);
        if (!image.future.isDone()) history.screenshotState(drop.getId(), "Capturing");
        image.future.whenComplete(
                (bytes, error) ->
                        clientThread.invokeLater(
                                () -> {
                                    if (!active
                                            || drop.getSession() != history.getSession()
                                            || image.epoch != screenshotEpoch) return;
                                    if (bytes != null) history.screenshot(drop.getId(), bytes);
                                    else history.screenshotState(drop.getId(), "Not captured");
                                    refresh();
                                }));
        if (heldReason == null
                && config.postingEnabled()
                && config.automatic()
                && allowedWorld()
                && !result.indices.isEmpty()) {
            List<Destination> targets =
                    routes == null ? List.of() : routes.matching(drop, null, true);
            if (targets.isEmpty())
                history.setReason(drop.getId(), "No matching Discord destination");
            for (Destination destination : targets) {
                List<Integer> selected = new ArrayList<>();
                long minimum = routes.minimumValue(destination.getId(), drop);
                long total = 0;
                for (int index : result.indices)
                    total =
                            LootFilter.add(
                                    total,
                                    Math.max(
                                            0,
                                            LootFilter.value(
                                                    drop.getItems().get(index),
                                                    config.valueBasis())));
                for (int index : result.indices)
                    if (drop.isPet()
                            || minimum <= 0
                            || (config.rewardTotal()
                                            ? total
                                            : LootFilter.value(
                                                    drop.getItems().get(index),
                                                    config.valueBasis()))
                                    >= minimum) selected.add(index);
                if (selected.isEmpty()) {
                    history.setReason(drop.getId(), "Below destination value threshold");
                    continue;
                }
                send(
                        drop,
                        selected,
                        destination,
                        false,
                        config.automaticScreenshots() ? image : noImage());
            }
        }
        refresh();
    }

    private boolean allowedWorld() {
        return config.seasonalWorlds()
                || Collections.disjoint(
                        client.getWorldType(),
                        EnumSet.of(
                                WorldType.SEASONAL,
                                WorldType.DEADMAN,
                                WorldType.BETA_WORLD,
                                WorldType.NOSAVE_MODE,
                                WorldType.TOURNAMENT_WORLD));
    }

    private void manualPost(String id, Integer index, String destinationId, boolean repeat) {
        HistoryStore.Entry entry = history.get(id);
        if (!active || entry == null) {
            refresh();
            return;
        }
        if (!config.postingEnabled()) {
            history.setReason(id, "Enable Discord posting in settings before sending");
            refresh();
            return;
        }
        if (routes == null) {
            history.setReason(id, "Correct destination settings before sending");
            refresh();
            return;
        }
        LootDrop drop = entry.getDrop();
        List<Integer> indices = indices(drop, index);
        List<Destination> targets = routes.matching(drop, destinationId, false);
        if (targets.isEmpty())
            history.setReason(
                    id, "No matching destination; select a destination or adjust routing");
        for (Destination destination : targets)
            send(
                    drop,
                    indices,
                    destination,
                    repeat,
                    new CapturedImage(
                            screenshotEpoch,
                            CompletableFuture.completedFuture(
                                    config.manualScreenshots() ? history.screenshot(id) : null)));
        refresh();
    }

    private static List<Integer> indices(LootDrop drop, Integer index) {
        List<Integer> result = new ArrayList<>();
        if (index != null) {
            if (index >= 0 && index < drop.getItems().size()) result.add(index);
        } else for (int i = 0; i < drop.getItems().size(); i++) result.add(i);
        return result;
    }

    private void send(
            LootDrop drop,
            List<Integer> indices,
            Destination destination,
            boolean repeat,
            CapturedImage image) {
        HistoryStore.Reservation reservation =
                history.reserve(drop.getId(), indices, destination.getId(), repeat);
        if (reservation == null) return;
        EmbedOptions options = embedOptions();
        DiscordClient sender = discord;
        long run = lifecycle, delivery = deliveryEpoch;
        image.future.whenComplete(
                (bytes, error) ->
                        clientThread.invokeLater(
                                () -> {
                                    if (!active
                                            || run != lifecycle
                                            || drop.getSession() != history.getSession()) return;
                                    if (delivery != deliveryEpoch) {
                                        history.update(
                                                reservation,
                                                DeliveryStatus.FAILED,
                                                "Settings changed before delivery; review and post"
                                                        + " manually",
                                                null);
                                        refresh();
                                        return;
                                    }
                                    if (!config.postingEnabled()) {
                                        history.update(
                                                reservation,
                                                DeliveryStatus.FAILED,
                                                "Discord posting disabled before delivery",
                                                null);
                                        refresh();
                                        return;
                                    }
                                    sender.submit(
                                            destination,
                                            drop,
                                            new ArrayList<>(reservation.getIndices()),
                                            options,
                                            image.epoch == screenshotEpoch ? bytes : null,
                                            new DiscordClient.Listener() {
                                                public void onSending() {
                                                    history.update(
                                                            reservation,
                                                            DeliveryStatus.SENDING,
                                                            "Sending",
                                                            null);
                                                    refresh();
                                                }

                                                public void onResult(
                                                        DeliveryStatus state,
                                                        String detail,
                                                        String messageId) {
                                                    history.update(
                                                            reservation, state, detail, messageId);
                                                    refresh();
                                                }
                                            });
                                }));
    }

    private EmbedOptions embedOptions() {
        return EmbedOptions.builder()
                .embedded(config.embedded())
                .showPlayer(config.showPlayer())
                .showAccountBadge(config.showAccountBadge())
                .showGeValue(config.showGeValue())
                .showHaValue(config.showHaValue())
                .showRarityFraction(config.rarityFraction())
                .showRarityPercent(config.rarityPercent())
                .showUnknownRarity(config.unknownRarity())
                .showTimestamp(config.timestamp())
                .showCombatLevel(config.combatLevel())
                .showRaidMetadata(config.raidMetadata())
                .showThumbnail(config.showThumbnail())
                .wikiLinks(config.wikiLinks())
                .accentColor(config.accentColor().getRGB() & 0xFFFFFF)
                .build();
    }

    private void sendTest(String destinationId) {
        if (!active || !config.postingEnabled()) {
            showMessage("Enable Discord posting in the plugin settings first.");
            return;
        }
        if (destinations.isEmpty()) {
            showMessage("Add a valid webhook destination first.");
            return;
        }
        LootDrop sample =
                baseDrop(
                                "Test notification",
                                "TEST",
                                List.of(
                                        new DropItem(
                                                -1,
                                                "Test message — no drop received",
                                                1,
                                                -1,
                                                -1,
                                                Rarity.unknown())))
                        .player(character.isEmpty() ? "Loot Herald" : character)
                        .build();
        history.add(sample, "Manually requested connection test");
        for (Destination destination : destinations)
            if (destinationId == null || destinationId.equals(destination.getId()))
                send(sample, List.of(0), destination, false, noImage());
        refresh();
    }

    private void previewDrop(String id, Integer index) {
        HistoryStore.Entry entry = history.get(id);
        if (entry == null) return;
        MessagePreview.show(
                panel,
                new DiscordFormatter()
                        .format(
                                entry.getDrop(),
                                indices(entry.getDrop(), index),
                                embedOptions(),
                                false),
                entry.getScreenshotState());
    }

    private void resetTransientState() {
        pendingLoot.clear();
        petImages.clear();
        petTimes.clear();
        collectionSignals.clear();
        pets.clear();
        rewards.clear();
        character = "";
        collectionPopup = "";
        collectionPopupArmed = false;
    }

    private void showMessage(String text) {
        SwingUtilities.invokeLater(
                () ->
                        JOptionPane.showMessageDialog(
                                panel, text, "Loot Herald", JOptionPane.INFORMATION_MESSAGE));
    }

    private void refresh() {
        HistoryPanel p = panel;
        if (p != null) p.refresh();
    }

    private CapturedImage noImage() {
        return new CapturedImage(screenshotEpoch, CompletableFuture.completedFuture(null));
    }

    private static final class CapturedImage {
        final long epoch;
        final CompletableFuture<byte[]> future;

        CapturedImage(long epoch, CompletableFuture<byte[]> future) {
            this.epoch = epoch;
            this.future = future;
        }
    }

    private static final class PendingLoot {
        final LootDrop drop;
        final int tick;
        final CapturedImage image;

        PendingLoot(LootDrop drop, int tick, CapturedImage image) {
            this.drop = drop;
            this.tick = tick;
            this.image = image;
        }
    }
}

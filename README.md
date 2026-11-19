# Loot Herald — for Discord

![Loot Herald](icon.png)

**Loot notifications and a current-session drop history for RuneLite.**
By **Corndwell**.

Loot Herald records observed loot in a searchable sidebar and lets you decide what reaches Discord. Automatically announce qualifying drops, manually post an individual item, or share the remaining items from an entire reward.

Messages use a Discord embed with the player, item icon, source, configurable rarity and price fields, and an optional original screenshot. History includes filtered drops, so inexpensive or excluded items can still be shared manually.

## Contents

- [Getting started](#getting-started)
- [Using the session history](#using-the-session-history)
- [Avoiding duplicate posts](#avoiding-duplicate-posts)
- [Automatic filters](#automatic-filters)
- [Destinations and routing](#destinations-and-routing)
- [Message appearance](#message-appearance)
- [Screenshots and privacy](#screenshots-and-privacy)
- [Bosses, raids, chests and clues](#bosses-raids-chests-and-clues)
- [Pets](#pets)
- [Rarity information](#rarity-information)
- [Troubleshooting](#troubleshooting)
- [Credits and licences](#credits-and-licences)

## Getting started

### 1. Enable loot detection

Enable RuneLite's built-in **Loot Tracker** as well as **Loot Herald**. Loot Herald uses the loot events supplied by Loot Tracker. The sidebar reports when that source is unavailable.

Loot Herald records new observed rewards while it is running. It does not import saved loot or recover drops received before it was enabled. Its session history is independent of Loot Tracker's own saved-history preferences.

### 2. Create a Discord webhook

In Discord, open **Server Settings → Integrations → Webhooks**, create a webhook, choose its destination channel, and copy its webhook URL. Choose the webhook's name and avatar there; those are the bot identity displayed above the message.

You need permission to manage webhooks in that server. See Discord's [webhook setup guide](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks) if the option is unavailable.

A webhook URL grants permission to post to its channel. Keep it private.

### 3. Add the destination

Open the Loot Herald sidebar and click **Destinations…**. Enter a name such as **Personal** or **Clan**, paste the complete webhook URL copied from Discord into the masked URL field, then click **Save destinations**.

Up to **five different destinations** are supported. Names must be unique, ignoring case. The editor rejects duplicate webhook endpoints and validates every row without sending a message. Leave both fields in a row blank to remove it; **Cancel** leaves the saved destinations unchanged.

For advanced configuration imports, the underlying secret **Webhook destinations** setting retains this multiline format:

```text
Personal | https://discord.com/api/webhooks/WEBHOOK_ID/WEBHOOK_TOKEN
Clan | https://discord.com/api/webhooks/WEBHOOK_ID/WEBHOOK_TOKEN
```

Replace each example URL with the complete URL copied from Discord. Imported URLs without names receive automatic names, and identical normalized URLs are deduplicated. Use the sidebar editor for routine changes; RuneLite's generic secret-setting control is a single masked line.

### 4. Allow posting and test it

**Enable Discord posting** starts **off**. Turn it on when you are ready to allow automatic, manual and test messages.

Open the sidebar, select a destination under **Post to**, and click **Test destination**. Selecting **All destinations** tests every configured destination.

**Automatic posting** defaults to on, but cannot send while the main posting switch is off. For manual-only use, turn **Automatic posting** off. History continues recording. Screenshots are off by default and have separate controls.

## Using the session history

Each observed kill, chest, casket or reward claim becomes a reward card containing its source, original time, player, item stacks, posting reason, screenshot state and destination-specific delivery status.

- **Search drops** searches item names, sources and player names.
- Click a reward's title to expand or collapse it.
- Larger rewards initially show four items; **Show all items** reveals the rest.
- **View** opens a local message preview of the outgoing message without posting. Discord controls the final rendered appearance.
- **Post to Discord** sends an individual item even if automatic value, rarity or item filters excluded it.
- **Post reward** sends the unsent items from a reward together.
- **Post remaining** sends only eligible unsent or failed item/destination combinations. It skips combinations already sent or marked uncertain.
- **Pause automatic posts** continues recording history and leaves manual posting available.
- **Clear session** forgets local history. It does not delete messages already delivered to Discord.

The **Post to** selection determines which destination statuses and actions you are viewing. Success in one channel does not mark the item as sent to another channel.

### What counts as a session?

History and screenshots are held in memory only. Logging out, changing accounts, disabling the plugin or closing RuneLite ends the session. World hopping preserves it. **Clear session** begins an empty history immediately.

Plugin settings, including destinations, remain saved. Previously delivered Discord messages remain in Discord.

| Data | Memory limit |
|---|---:|
| Reward events | 10,000 |
| Original screenshots | 50 |
| Combined screenshot memory | 64 MiB |

The oldest entries or images expire when a limit is reached. The sidebar reports expired rewards and missing screenshots. A reward can remain available after its image expires.

No session history is uploaded merely because it appears in the sidebar. Posting sends the selected message and any enabled available image to the chosen Discord destination.

## Avoiding duplicate posts

Delivery is tracked separately for every item and destination. Automatic and manual actions share the same delivery state. Buttons are disabled while a selected delivery is pending.

| Status | Meaning |
|---|---|
| Not sent | No confirmed or pending delivery to this destination |
| Queued | Waiting to send |
| Sending | A request is in progress, or waiting through a recognized rate limit |
| Sent | Discord returned a confirmed message |
| Failed | No confirmed delivery; the entry can be tried again |
| Delivery uncertain | Discord may have received the message, but confirmation was lost or ambiguous |

**Post again** is a deliberate resend. It asks for confirmation and can create another Discord message. Check the channel first, especially when delivery is uncertain.

A whole-reward action does not repost an item already sent individually. If an item reached one destination but failed at another, **Post remaining** handles only the remaining eligible combinations.

Discord rate limits are retried using Discord's requested delay, up to three retries. Ambiguous network failures and server responses are **not automatically retried**, because doing so could post the same drop twice. They are marked **Delivery uncertain** for review.

Protection belongs to retained current-session entries. Clearing the session removes their delivery state. Other plugins and other RuneLite clients have separate state: turn off overlapping automatic notifications in Dink, Discord Rare Drop Notifier or similar plugins if they post to the same channel.

### Reopened reward screens

Some activities show an existing reward again when a chest is reopened or items are partially withdrawn. Loot Herald uses observed completion/chest counts and reward contents to reject correlated repeats.

If a later reward might be an old screen but there is insufficient evidence to decide, the history displays:

> Reward may already have been recorded; automatic posting held

The candidate remains available for inspection and manual posting. Compare it with the previous reward and Discord before sharing it. This avoids automatically announcing an uncertain repeated screen while preserving a potentially genuine identical reward.

Separate ordinary kills, clue openings and keyed-chest rewards are not discarded merely because their contents match.

## Automatic filters

These settings affect **automatic posting**. Filtered loot remains in history; manual posting bypasses these qualification thresholds.

| Setting | Default | Behavior |
|---|---|---|
| Minimum value (GP) | 50,000 | Minimum stack value |
| Value used for filters | Higher value | The greater of GE and High Alchemy |
| Minimum rarity (1 in) | 64 | Match supported odds of 1 in 64 or rarer |
| Combine value and rarity | Value or rarity | Either threshold qualifies |
| Always post known uniques | On | Recognized uniques bypass value/rarity thresholds |
| Always include items | Empty | Explicit ordinary-item exceptions |
| Exclude items | Empty | Exclude matching ordinary items from automatic messages |
| Allowed sources | Empty | Empty permits all observed sources |
| Allowed players | Empty | Empty permits all observed local accounts |
| Filter by reward total | Off | Use the combined value of non-excluded items |
| Include full qualifying reward | Off | Include other non-excluded items when any item qualifies |
| Allow seasonal worlds | Off | Permit automatic posts on seasonal, Deadman, beta and other temporary/testing worlds |

Price bases are **Grand Exchange**, **High Alchemy** and **Higher value**. Values include quantity. High Alchemy values do not subtract rune costs.

Threshold combinations:

- **Value or rarity:** either condition passes.
- **Value and rarity:** both pass.
- **Value only:** rarity has no effect.
- **Rarity only:** value has no effect.

A minimum value of **0** disables the value floor, including for items without a market price.

Unknown rarity does not pass a rarity test. Unique classification is separate from a numerical drop rate: an item can be a recognized unique while its precise odds are unavailable.

### Item and source matching

List settings accept commas or newlines. Matching is case-insensitive, and spaces around entries are ignored.

An entry matches a complete name or item ID. Use `*` to match any text:

```text
Tumeken's shadow*
*crystal*seed
Clue scroll*
```

Example **Allowed sources**:

```text
Tombs of Amascut*
Corrupted Gauntlet
Clue Scroll (Master)
```

**Allowed players** uses RuneScape display names. It filters the local player's automatic announcements; it does not observe other players' loot.

For ordinary items, **Exclude items takes priority** over **Always include items** and **Always post known uniques**. Source and player restrictions apply before item qualification.

Pet awards have separate switches and bypass ordinary item/value/rarity qualification. Applicable source/player restrictions, posting controls and routing still apply.

### Reward totals and full-reward messages

- **Filter by reward total** changes the value used to decide whether items qualify.
- **Include full qualifying reward** changes which non-excluded items appear after something qualifies.

For example, five 20,000 GP stacks total 100,000 GP. With a 50,000 GP threshold, **Value only** and reward-total filtering off, none qualifies by value. With reward-total filtering on, the combined reward meets the threshold.

## Destinations and routing

| Setting | Default |
|---|---|
| Enable Discord posting | Off |
| Webhook destinations | Empty |
| Automatic posting | On |
| Destination rules | Empty |

With **Destination rules** empty, qualifying automatic drops go to all configured destinations.

Each optional rule uses four fields separated by `|`:

```text
Destination name | Source wildcard | Player wildcard | Minimum GP
```

Example:

```text
Personal | * | Corndwell | 0
Clan | * | * | 1000000
Raids | Tombs of Amascut* | * | 0
```

Destination names correspond to **Webhook destinations** and must be unique, ignoring case and surrounding spaces. Use `*` for any source or player; a blank source/player field also means any. Enter minimum GP as a plain non-negative integer: `1000000`, not `1m`.

Once rules are present, only destinations with a matching rule are selected. Several matching rules for one destination are alternatives: the lowest applicable minimum GP is used, and the drop is not sent multiple times because several rules matched. Destination and wildcard matching is case-insensitive.

Rules add destination conditions after a drop qualifies for automatic posting. A route minimum of `0` does not make every ordinary item qualify by itself. Ordinary-loot route thresholds use the configured price basis and check each selected stack, or the combined selected reward when **Filter by reward total** is on. Pet announcements bypass the route's GP threshold.

Manual posting to **All destinations** respects source/player routing but ignores route minimum GP. Selecting a specific destination explicitly overrides those routing conditions for that manual post.

Discord HTTPS webhook URLs are accepted; arbitrary third-party relay URLs are not. An existing Discord thread can be targeted with `?thread_id=THREAD_ID`. Automatic forum-thread creation is not supported.

## Message appearance

A single-item embed places the player and account badge above a loot description, with an item thumbnail and optional **Rarity**, **HA value** and **GE value** fields. Multi-item rewards list the selected stacks and available totals. An enabled screenshot appears with the same notification.

The original receipt time and recorded prices are retained for later manual posts.

| Setting | Default |
|---|---|
| Rich embeds | On |
| Player name | On |
| Account badge | On |
| Item thumbnail | On |
| Wiki links | On |
| GE value | On |
| High Alchemy value | On |
| Rarity fraction | On |
| Rarity percentage | On |
| Show unknown rarity | Off |
| NPC combat level | Off |
| Reward details | On |
| Drop timestamp | On |
| Embed colour | Gold (`#D7AC52`) |

Turn **Rich embeds** off for plain text. Visual controls such as badges, thumbnails and accent colour apply to embeds.

**Reward details** includes available observed information such as ToA raid level and team size. Unavailable information is omitted. Unknown prices remain unknown.

Very large messages are shortened to Discord's limits. The complete retained reward remains in local history, with individual-item actions.

## Screenshots and privacy

| Setting | Default | Behavior |
|---|---|---|
| Automatic screenshots | Off | Attach an original image to qualifying automatic posts |
| Manual screenshots | Off | Attach the original cached image to manual posts when available |
| Capture filtered drops | Off | Keep receipt-time images for filtered rewards for possible later use |
| Mask chat and private messages | On | Black out chat, private-message overlays and chat input in captured images |

Capture and later posting are distinct. When an automatic or manual screenshot option is on, qualifying rewards can have an original image retained. Enable **Capture filtered drops** to retain images for below-threshold or excluded rewards too. That option can keep local session images even when both posting-image options are off; it does not upload them by itself. Turning on manual screenshots after an event does not recreate an image that was never captured.

If an image is unavailable, expired or could not be captured, history explains its state. A manual post uses an available **original** image or sends without one; it never takes a current screenshot and presents it as belonging to an earlier drop.

The game view can show information outside masked chat areas, including names, inventory, location and other overlays. Enable images only when you are comfortable sharing what the view shows. Leave screenshot options off for text-only notifications.

Changing the chat-masking setting discards cached images and cancels pending deliveries so an older image cannot use the wrong privacy setting. Messages already submitted to Discord cannot be recalled. Custom overlays outside the game chat areas can still appear in an image.

Screenshots are session-only, subject to the 50-image/64 MiB limit, and are not written to a local screenshot archive by Loot Herald.

## Bosses, raids, chests and clues

Loot Herald records supported loot events supplied by RuneLite's Loot Tracker. Coverage can change with game and RuneLite updates; not every activity or reward mechanism necessarily produces a usable event.

| Source | Representation |
|---|---|
| NPCs and bosses | One observed award with its item stacks; NPC information where available |
| CoX, ToB and ToA | A grouped reward when RuneLite detects its interface; optional observed raid details |
| Gauntlet and Corrupted Gauntlet | Distinct activity names, including normalized Hunllef loot sources |
| Barrows and Moons of Peril | A grouped chest reward; Lunar Chest displays as Moons of Peril |
| Clue scrolls | A casket reward with its beginner/easy/medium/hard/elite/master tier |
| Keyed chests | RuneLite's reported source, such as Crystal, Brimstone or Larran's chest |
| Pools and minigames | Individual claims or batches reported by RuneLite, including Tempoross, Guardians of the Rift and Wintertodt |
| Bags, crates and caches | An observed opening reward; several openings may form a batch |
| Other recognized sources | Retained under their reported name even when rarity data is unavailable |

A clue scroll received from a monster is part of that monster's loot. Opening its eventual casket is a separate reward event.

Receiving a reward container and later opening it can also create separate entries. Those represent different stages of obtaining loot, not necessarily two independent gains in wealth.

A reward detected when its interface opens does not necessarily mean every item has been withdrawn. Closing, reopening or partly emptying the interface must not be treated as a fresh award without evidence.

Loot awarded while the plugin or its upstream detector was disabled is not reconstructed. Other players' drops and team members' private raid rewards are not collected.

## Pets

| Setting | Default | Behavior |
|---|---|---|
| New pet notifications | On | Announce an actual detected pet award independently of ordinary loot value/rarity |
| Duplicate pet rolls | Off | Optionally announce a “would have been followed” roll, labelled as a duplicate |

Automatic pet announcements still need **Enable Discord posting**, an applicable destination and automatic posting to be active. A recorded pet can be posted manually.

The detector combines trusted acquisition messages and known pet items, then briefly waits for related information to identify the pet and attach collection-log context where available.

For the best chance of a named notification, enable the game's **untradeable drop notification** and **collection log notification** chat options. If the pet cannot be identified, the notification stays generic rather than guessing.

A collection-log message by itself does not prove a new pet award. Picking up an existing pet, changing its appearance or reclaiming it must not be announced as a new drop. The plugin does not infer ownership by scanning the bank, inventory or follower.

Recognition follows known item forms and messages. New pets or changed messages can need an update. Pet rarity is not guessed from ordinary NPC rates.

## Rarity information

Rarity uses a bundled reference snapshot, not a live lookup. The current snapshot was reviewed on **19 November 2026**. Sources and licences are listed in [Third-party notices](THIRD_PARTY_NOTICES.md).

The plugin distinguishes:

- **A recognized unique:** an item explicitly classified as unique for its source.
- **A supported numerical rate:** a reference probability suitable for the lookup.
- **Unknown or context-dependent odds:** no precise rate is claimed.

Difficulty, contribution, points, team size and conditional tables can make fixed numbers misleading. Unsupported exact probabilities are omitted. A purple-item selection rate must not be presented as an unconditional chance per raid.

Reference rates can age or involve upstream approximations. A reviewed snapshot date does not guarantee every entry matches the latest game update. Rarity is information for filtering and display, not a promise about the next drop.

## Troubleshooting

| Symptom | What to check |
|---|---|
| No history | Enable Loot Herald and built-in Loot Tracker. Read the sidebar diagnostic. Confirm the activity produces a supported loot event. |
| History but no posts | Check Enable Discord posting, destinations, Automatic posting, Pause automatic posts, filters, routes and world type. |
| A common item posted | Default matching is value **or** rarity. Known uniques and always-include rules can also qualify items. |
| An included item was skipped | Ordinary-item exclusions take priority. Source/player filters and destination rules also apply. |
| A valuable whole reward was skipped | By default, stacks are checked separately. Enable Filter by reward total for combined value. |
| Manual All reaches fewer channels | Source/player destination rules still apply. Choose a specific destination for an explicit manual choice. |
| Invalid destination | Paste the complete Discord webhook URL, not a channel link or bot token. Use one `Name | URL` per line. |
| Failed delivery | Read the status. Check webhook validity, channel permissions, queue capacity and rate limits. |
| Delivery uncertain | Check Discord before Post again. Confirmation may have been lost after acceptance. |
| Reward held for review | It may be a reopened or partly withdrawn reward. Compare it with the prior event before posting. |
| Duplicate messages | Check other notifier plugins/clients, deliberate resends and whether the session was cleared. |
| No screenshot | Check settings, whether the original image was captured, its history state and memory limits. |
| Missing rarity | It may be unknown or context-dependent. Show unknown rarity makes that explicit. |
| Generic pet name | Enable untradeable and collection-log notifications. Some acquisition signals do not identify the pet. |
| History disappeared | Logout, account changes, plugin shutdown, Clear session and memory limits can remove it. |

If a webhook URL is exposed, delete or replace the webhook in Discord and update Loot Herald. Removing the URL from a local setting does not revoke it.

## Credits and licences

Loot Herald is by **Corndwell**. Its application code uses the [BSD 2-Clause licence](LICENSE).

The project acknowledges MasterKenth, BossHuso and the Discord Rare Drop Notifier contributors, RuneLite contributors, and Dink. Rarity data credits OSRS Wiki contributors, Flipping Utilities, pajlads and Dink contributors.

Bundled data has separate licence terms. See [Third-party notices](THIRD_PARTY_NOTICES.md) and [Pet catalog sources and notices](PET_DATA_NOTICES.md).

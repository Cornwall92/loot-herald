# Third-party notices

Loot Herald's application code is licensed under the repository's BSD-2-Clause license. Bundled reference data has separate licensing as described below. The application license does not relicense that data.

## OSRS Wiki rarity reference data — CC BY-NC-SA 3.0

Files:
- `src/main/resources/rarity/npc-drops.json`
- `src/main/resources/rarity/thieving.json`
- `src/main/resources/rarity/event-uniques.json` (Wiki-derived classifications, plus the source attribution below)

Attribution: Old School RuneScape Wiki contributors (https://oldschool.runescape.wiki/), parsed by Flipping Utilities (https://github.com/Flipping-Utilities/parsed-osrs), with the NPC data transformed and maintained by pajlads and Dink contributors (https://github.com/pajlads/DinkPlugin).

License: Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported.
License deed: https://creativecommons.org/licenses/by-nc-sa/3.0/
Legal code: https://creativecommons.org/licenses/by-nc-sa/3.0/legalcode

The NPC and thieving snapshots were taken from Dink commit `ae769add57a4a91274c2e3d4ca1c702ec23b13e4`, reviewed on 2026-11-19:
- https://github.com/pajlads/DinkPlugin/blob/ae769add57a4a91274c2e3d4ca1c702ec23b13e4/src/main/resources/npc_drops.json
- https://github.com/pajlads/DinkPlugin/blob/ae769add57a4a91274c2e3d4ca1c702ec23b13e4/src/main/resources/thieving.json
- Dink's attribution: https://github.com/pajlads/DinkPlugin/blob/ae769add57a4a91274c2e3d4ca1c702ec23b13e4/README.md#credits
- Transformation description: https://github.com/pajlads/DinkPlugin/blob/ae769add57a4a91274c2e3d4ca1c702ec23b13e4/src/test/java/dinkplugin/RarityCalculator.java

The two Dink JSON snapshots are unchanged apart from final newline normalization. Loot Herald independently interprets them conservatively: unknown or overlapping conditions do not become a claimed exact rate; dynamic encounter sources are excluded; rates are labelled reference estimates. The upstream conversion rounds some denominators and converts qualitative labels to estimates. The data is neither a live API nor a guarantee of current or player-specific odds.

The unique classification data was extracted from `EventRarity.java` at original plugin commit `98d54c918af4fa086db873b6c7231c31a524dac9`:
https://github.com/BossHuso/discord-rare-drop-notificater/blob/98d54c918af4fa086db873b6c7231c31a524dac9/src/main/java/com/masterkenth/EventRarity.java

Changes: omitted pet classification and all original event probabilities; replaced the obsolete direct Gauntlet weapon entry with the enhanced crystal weapon seed; separated Corrupted Gauntlet classification; added named Nex, Araxxor, Desert Treasure II, Yama and Phantom Muspah uniques. Symbolic item names were resolved against RuneLite's ItemID catalog. Derived classification data remains under CC BY-NC-SA 3.0. No pet odds are inferred. This classification is an explicit maintained allowlist, not comprehensive recognition of every unique item.

## Dink source reference — BSD 2-Clause

The data format and documented pickpocket behavior were studied from Dink. Loot Herald's matching implementation is independently written. Dink's notice is included for completeness:

BSD 2-Clause License

Copyright (c) 2022, Jake Barter
All rights reserved.

Copyright (c) 2022, pajlads

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Discord Rare Drop Notifier source-derived classification — BSD 2-Clause

The event unique list was derived from the source described above. Original notice:

BSD 2-Clause License

Copyright (c) 2020, MasterKenth
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

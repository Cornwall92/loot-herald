# Pet catalog sources and notices

The pet ID/name catalog in PetDetector uses the generated RuneLite gameval ItemID catalog at tag runelite-parent-1.13.7 (blob 971119498085910de0c277ad6d902d6bcf27d4d0). Values are referenced through gameval constants rather than duplicated numeric IDs. Only real pet items/forms are included; placeholders and morph-unlock ingredients are excluded.

Source: https://github.com/runelite/runelite/blob/runelite-parent-1.13.7/runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java

BSD 2-Clause License

Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
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


Detection design was cross-checked against Dink's pet tests and message handlers at ae769add57a4a91274c2e3d4ca1c702ec23b13e4. Loot Herald's state machine and tests are independently implemented; no drop-rate or luck tables were copied. Dink reference: https://github.com/pajlads/DinkPlugin/blob/ae769add57a4a91274c2e3d4ca1c702ec23b13e4/src/main/java/dinkplugin/notifiers/PetNotifier.java

This detector accepts only game acquisition chat or known pet items in an authoritative loot award. Collection/untradeable/clan messages only enrich existing pending acquisitions. It cannot detect acquisition from arbitrary inventory/follower changes and intentionally does not try. Names/sources that remain unknown are displayed as unknown, not guessed from a preceding boss. A pet pickup, transformation or reclaim is never passed to onLoot by integration.

The Dink reference's license is retained below for the message-pattern reference and attribution:

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

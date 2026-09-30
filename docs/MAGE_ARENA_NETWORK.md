# Mage Arena: local prototype and proposed network integration

## Implemented locally

The prototype casts locally and checks loaded players' world tiles on each client tick.
Warning shadows do not hit. During `DANGER_MS`, a matching X/Y/plane marks the player out,
except the caster of that spell. Names are deduplicated case-insensitively until the arena
or round resets. AnnouncementOverlay shows `<RSN> Out` for 2.5 seconds using the existing
Mario Party rainbow text and fade. Simultaneous eliminations get separate lines.

These are local observations, not server-confirmed eliminations. Other clients do not
receive the casts or banners. No HTTP requests, position reports, or WebSocket messages
are added. Players outside the loaded scene cannot be checked. The standalone prototype
freezes its dodger roster from joined, seated PLAYER entries when connected to a game (excluding the starter/mage). Spectators are excluded, regardless of how many are connected. Without a game roster, standing in the arena at Start opts a nearby player into the offline test. With an empty roster, solo animation testing remains available and no winner or payout is assigned.
Toggle "Enable Mage Arena prototype" off/on to rebuild it and clear eliminations.

## Proposed backend contract (not yet implemented or deployed)

Follow the existing Brutus Attack attack-broadcast/self-report design. Do not reuse Flame
Field's `confirm-arena-elimination` endpoint; it belongs to a different minigame.

1. Broadcast round setup with a round ID, shared arena anchor, assigned mage and eligible
   participants. Replace the per-client prototype arena placement with that shared arena.
2. Accept one authenticated cast request from the assigned mage with a unique client cast
   ID, round ID and target X/Y/plane. Validate role, active round, target and cooldown.
   Use the cast ID for idempotency; duplicate requests must not create additional spells.
3. Broadcast one `MAGE_ARENA_SPELL_CAST` event with round ID, spell ID, caster, target,
   server time, detonation time and danger-end time. Clients derive both visual stages
   from this event; do not send separate warning, detonation or expiration requests.
   Specify server/client clock synchronization and late-event handling before integration:
   starting a fresh warning on receipt would give different clients different hit windows.
4. Each eligible surviving non-mage checks ONLY its own tile locally during that spell's
   danger window. Send one authenticated elimination report with round ID and spell ID.
   Latch a pending report to avoid one request per tick, and use a bounded retry of the
   same idempotency key after transient failure. Do not retry indefinitely or report misses.
5. Validate membership, round, spell and elimination status, then broadcast
   `MAGE_ARENA_PLAYER_ELIMINATED` with round ID, spell ID and RSN. Record the elimination
   and show the banner once. Replay/catch-up events restore state without old banners
   or hit reports. Server validation of these identifiers is not independent verification
   of the player's physical position: this remains a trusted self-report model.

Client integration points: `ApiClient` for cast/elimination requests; `Events` for event
names; the existing `EventSocket` transport; MageArenaPresentation for event folding,
spell identity/timing and `recordElimination(rsn, catchingUp)`; RunePartyPlugin's client
tick for the local-player-only check. Gate the network path on a real Mage Arena session;
never send standalone prototype observations to the production service.

Budget: one cast request plus one broadcast per spell; at most one logical elimination
report per participant per round, plus its result broadcast. No position heartbeat,
per-frame traffic, miss reports or animation-completion messages. Network retries and
WebSocket reconnects are additional traffic. At a 600 ms cast cooldown the upper cast
request rate is about 1.67/s; each broadcast fans out to all connected participants.

## Round begin, timer and scoring

Minigame.java is only the registry interface. MinigamePresentation dispatches the server's
MINIGAME_ROUND_BEGIN to the selected feature's onRoundBegin, after the applicable ready /
countdown / BEGIN presentation. Brutus Bullet additionally uses BRUTUS_ROUND_STARTED
for each dash round. Mage Arena now starts its clock in onRoundBegin as well.
For standalone testing, Shift-click an arena tile and choose Start Mage Arena. This
freezes the prototype roster and calls that hook without casting. Subsequent Shift-clicks
cast. A real active Mage Arena session must wait for the shared server begin signal.

Rune Party's Mage Arena duration setting defaults to 45 seconds (1-3600 allowed). The
value is captured at round begin, so changing it cannot extend an ongoing round. At the
deadline, casting, damage checks and remaining spell effects stop. Toggle the prototype
off/on to reset. This local timer adds no network requests. The backend must eventually
supply a shared round deadline/duration and restore remaining time on reconnect rather
than granting a fresh full duration from a replayed BEGIN event.

Rules: the mage wins immediately when all dodgers are eliminated. Otherwise the dodgers
win at the deadline. On a dodger win, survivors receive the full winner payout and
eliminated dodgers receive half that payout; the mage receives no winner payout. On a
mage win, the mage receives the full winner payout and dodgers receive no winner payout.
MageArenaPresentation.getRewardShare returns relative shares (1, 0.5, or 0) for local
inspection only. Actual coin amounts and half-coin rounding remain backend decisions.

Existing ApiClient.submitMinigameResult sends a player's raw integer score to
/submit-minigame-result. The client describes winner selection and coin awards as server
responsibilities; MinigamePresentation.handleMinigameEnded renders the server's results
and rewards. Mage Arena should have its own server outcome logic using its frozen roster
and confirmed eliminations, and publish MINIGAME_ENDED with the final results/rewards.
No score or coin payout request has been added to the standalone prototype.
## Player-count-dependent arena

Counts include the mage and only joined PLAYER seats, never SPECTATOR entries. A game
may contain any number of spectators while supporting at most eight players.

| Players | Arena |
| --- | --- |
| 2 | 4x4 |
| 3-4 | 6x5 |
| 5-6 | 7x7 |
| 7-8 | 8x8 |

Tune the dimensions in MageArenaPresentation.ArenaSize; each dimension is capped at 8.
Before round begin, roster count changes resize the prototype at its existing southwest
anchor. Once begun, its size and dodger roster stay fixed until reset, including after
eliminations. Offline testing uses the Mage Arena test players setting (default 4), not
the number of arbitrary players visible nearby. With a connected game, only seated
players can start the prototype; the test-player setting does not override the roster.
Toggle the prototype off/on to rebuild it after a completed round.
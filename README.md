# VyntricUHCCore

Single Paper plugin (Java 21, target `1.21.1-R0.1-SNAPSHOT`) combining the UHC core game
engine with the cross-team combat tracker (originally the separate `VyntricCrossteam`
plugin). Optional soft-dependency on PlaceholderAPI.

## Feature summary

**Login/auth** - `/register`, `/login`, `/changepass`. Salted + 10,000-round SHA-256
password hashing (no plaintext ever stored). Freezes movement/damage/chat/block-break/
interact for anyone not logged in, kicks after a configurable timeout, and flags possible
alt accounts to staff by shared IP on join.

**Waiting lobby glass boxes** *(new)* - every player is placed inside their own sealed
glass box the moment they join, for as long as the game is in the waiting lobby - classic
UHC "everyone loads in together" look. Set the location with `/vyntricuhc setlobby`
(stand where you want it, then run the command). All boxes clear and everyone is released
in the same instant `/vyntricuhc start` runs.

**World border** - configurable start/end size, shrink duration, damage-per-block, warning
distance. `/world border set <size> [seconds]`.

**Chunk pre-generation** - pre-loads the border area in small async batches on startup
(configurable chunks/tick) so the world doesn't lag-generate mid-game; game opens
automatically once it finishes.

**Teams** - `/vyntricuhc team create|add|list|accept|limit`. Right-click a player to
invite (owner only), right-click with a sword to kick. Configurable max size, optional
friendly fire, random spread-out teleport per team when the game starts.

**Game flow** - phases: `PREGENERATING -> WAITING -> GRACE_PERIOD -> PVP_ENABLED ->
DEATHMATCH -> ENDED`. `/vyntricuhc start|stop|restart`, adjustable meetup/grace period
length, automatic deathmatch trigger after N minutes (or immediately when grace ends),
manual PVP override.

**Combat logout punishment** - disconnecting mid-game turns you into a zombie holding all
your gear (100% drop chance); rejoin before it dies and you get your gear + spot back.

**Cross-team tracker** - `/track` (aliases `/vt`, `/crossteam`). Flags players who
repeatedly hit the same enemy team (cross-teaming/boost detection), tracks
projectile damage back to the shooter, broadcasts live alerts to staff, `/track top`
leaderboard, `/track reset <team>`, autosaves to `crossteam-data.yml`.

**Bounties** - `/vyntricuhc bounty add|list` - cosmetic points bounty, announced
server-wide, paid out (in the kill-feed message) to whoever gets the kill.

**Stats & leaderboard** - wins/kills/deaths per player, persisted to `stats.yml`,
`/vyntricuhc leaderboard`, `/vyntricuhc stats [player]`.

**Item nerfs** - golden apple cooldown + optional regen/absorption strip; splash/lingering
potion type blocking (negative effects and/or instant health).

**Scoreboard & tab list** - live sidebar and tab header/footer with placeholders:
`{time} {border_size} {alive_players} {alive_teams} {phase} {online}`.

**PlaceholderAPI expansion** - `%vyntricuhc_phase/time/border/alive_players/alive_teams/
wins/kills%` (only registers if PlaceholderAPI is installed).

**Discord webhook** - fires game-start/deathmatch/winner events to a webhook URL, off the
main thread.

**Confirmation guard** - risky commands (start/stop/restart/reload, pregen start, team
limit change, manual pvp toggle, world border set, resetpass, track reset) require being
run twice within a time window before they execute.

## Commands

| Command | Notes |
|---|---|
| `/vyntricuhc <sub>` | aliases `/vuhc`, `/vyntric` - see subcommands below |
| `/world border set <size> [seconds]` | |
| `/login <password>` / `/register <password> [confirm]` / `/changepass <old> <new>` | |
| `/resetpass <ign>` | admin - wipes a forgotten password |
| `/track <team\|player>` / `top` / `reset <team>` / `help` | aliases `/vt`, `/crossteam` |

`/vyntricuhc` subcommands: `start`, `stop`, `restart`, `border`, `deathmatch`, `team
<create|add|list|accept|limit>`, `pregen <start|status> [size]`, **`setlobby`**, `meetup
time <minutes>`, `passinfo <ign>`, `pvp <enable|disable|auto>`, `leaderboard`, `stats
[player]`, `bounty <add|list>`, `reload`.

## Permissions

| Permission | Default | Covers |
|---|---|---|
| `vyntric.uhc.admin` | op | all `/vyntricuhc` subcommands |
| `vyntric.uhc.world` | op | `/world border set` |
| `vyntric.uhc.resetpass` | op | `/resetpass` |
| `vyntric.track` | true | `/track` (team/player report) |
| `vyntric.track.top` | true | `/track top` |
| `vyntric.admin` | op | `/track reset` |
| `vyntric.alerts` | op | live cross-team-detected alerts |

## Key config sections (`config.yml`)

`auth`, `pregen`, `border`, `grace-period`, `deathmatch`, `combat-logout`, `golden-apple`,
`potions`, `teams`, **`lobby-cage`** *(new)*, `confirmation`, `discord`, `scoreboard`,
`tablist`, `messages`, `crossteam` (own `settings` + `messages` sub-sections).

### `lobby-cage` (new)

```yaml
lobby-cage:
  enabled: true          # auto-set to true by /vyntricuhc setlobby
  world: "world"
  center-x: 0
  center-y: 100
  center-z: 0
  size: 3                 # interior width/depth, forced odd, min 3
  height: 3                # interior height
  spacing: 6                # distance between boxes
  per-row: 5                 # boxes per row before wrapping
  material: GLASS
  clear-floor-on-release: true   # set false if teams.spread-on-start is also false
```

Run `/vyntricuhc setlobby` while standing at the spot you want the grid of boxes centered
on - do this in an open, flat area with nothing important nearby, since the box footprint
extends outward from that point based on `per-row` and `spacing`.

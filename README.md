# Knight Finder

A [RuneLite](https://runelite.net) plugin that finds worlds where a Knight of Ardougne is already
pinned down for pickpocketing, shared by other plugin users.

Knights are the best pickpocket in the game once they are not walking about. People keep them still
in two ways: trapping one in the house north of the Ardougne market so it can only shuffle between
two tiles, or "splashing" one in the south bank so it stays in combat with a player who never hurts
it. Either way the work is finding a world where somebody has already done that. This plugin does
the finding.

## What it does

- **Notices a pinned knight on your world.** A knight that has stayed within a two tile box for
  thirty seconds while somebody picks its pocket, or while it fights a player, counts as pinned.
  A knight that merely paused in the street does not.
- **Outlines it**, with how it is pinned, how many people are on it, and how long it has been there.
  This part works with sharing off.
- **Shares it** (opt-in) so other plugin users can see it, and **lists the knights other people have
  found** in a side panel.
- **Optionally notifies you** when a knight appears on a world you are not on.

Listings show whether the knight is held by a splasher or trapped by walls. A held knight lasts as
long as the splasher does; a trapped one lasts until somebody lets it out. Both are dimmed once
nobody has reported them for a while, and dropped soon after.

## How to use

1. Install and enable **Knight Finder**.
2. Open the plugin settings and enable **Enable shared knights**.
3. Open the Knight Finder sidebar panel and hop to a listed world.

Standing next to a pinned knight is all it takes to share it; there is nothing to click. Reports
are refreshed while the panel is open, and the **Refresh** button fetches them on demand.

## Privacy

A shared report contains the world, the knight's coordinates, a short name for the spot, whether the
knight is in combat or trapped, and the number of players who have pickpocketed it recently. It
does not contain your account name, the names of anybody at the knight, or anything else about
them; the count is a number, kept only for as long as the knight stays pinned.

Sharing is disabled by default because it communicates with a third-party service. As with any
online service, the service operator may receive your IP address when you enable it.

## Building

Requires JDK 11 or newer.

```
./gradlew build
```

The plugin can be run in a development client with `./gradlew run`.

## License

BSD 2-Clause. See [LICENSE](LICENSE).

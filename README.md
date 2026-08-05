# MythicMobsAddon

MythicMobsAddon is a Paper 1.12.2 addon for MythicMobs 4.12.0. MythicMobs' `ItemManager` and item YAML files remain the only item source of truth; the addon does not maintain a separate item library.

## Commands

- `/mythicmobsaddon items` opens the MM item browser. `/mma items` is also accepted.
- `/mythicmobsaddon reload` reloads the MythicMobs item manager. Requires `mythicmobsaddon.admin`.
- The game command does not expose a web subcommand; the web console remains available through its configured HTTP endpoint.

## Ownership boundary

New items created by the addon are stored in this file:

`plugins/MythicMobs/Items/MythicMobsAddon/items.yml`

Every item registered by MythicMobs is displayed directly from `ItemManager`. YAML files below
`plugins/MythicMobs/Items` can be edited or deleted from the web console after an explicit external
mutation confirmation; paths outside that directory and symbolic-link paths are rejected. Tag metadata
is stored in `plugins/MythicMobsAddon/tags.yml`, and the catalog is sorted by tag first and vanilla
material second.

The web console edits the live MM item registry and automatically refreshes after MythicMobs reloads or
external Items YAML changes. Its `导入` action accepts multiple local `.yml`/`.yaml` files, recognizes
modern MM item sections and legacy serialized `ItemStack` sections, previews IDs and conflicts, and only
writes after confirmation. Imported items are merged into the addon-managed MM item file without
overwriting an existing MM ID. The browser asks for the token with its native prompt and keeps the
application shell blank until authentication succeeds.

Common HTTP, JSON, form, multipart, authentication, scheduling, safe-path, pagination, and atomic-storage behavior is provided by `Lib`.

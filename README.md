# Discordbot • Live server status for Discord
Made by Mineconed

One small plugin that shows your Minecraft server live in Discord.
No other plugins needed. Works on **1.8 to 1.21** (Spigot and Paper).

## Features
- 🟢 **Live status embed** in a channel that updates itself
  (IP, players, TPS, staff online, uptime, version and who is online)
- 🔴 Turns red ("offline") when the server stops
- 💬 Slash commands: `/online` (who is online), `/status`, `/ip`
- 🤖 Bot activity shows how many players are online
- 🕵️ Vanished staff are hidden (SuperVanish, PremiumVanish, Essentials)
- 🌍 English and Danish built in. Make your own `messages_xx.yml`
- ✅ No special bot intents needed

## Setup
1. Make a bot on https://discord.com/developers/applications → Bot → Reset Token
2. Invite it: OAuth2 → URL Generator → scopes `bot` + `applications.commands`,
   permissions: View Channels, Send Messages, Embed Links
3. Put `Discordbot.jar` in `plugins/` and start the server
4. In `plugins/Discordbot/config.yml` set `bot.token`, `bot.guild_id`,
   `status.channel_id`, `server.name` and `server.ip`
5. Restart. Done!

## Commands
| Command | |
|---|---|
| `/discordbot` | Info about the bot |
| `/discordbot reload` | Reload config and messages |
| `/discordbot update` | Update the status now |
| `/discordbot new` | Post a new status message |

## Permissions
| Permission | |
|---|---|
| `discordbot.admin` | Use /discordbot (default: op) |
| `discordbot.staff` | Counts as staff in the status |

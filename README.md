# About this Bot

This is a Discord bot implementing a small card-collection game that allows users to collect new cards periodically, level up cards, answer trivia, and more!

The bot allows users to create cards and manage settings through a Web UI, and is designed to be as easy to set up as possible.

Note that drops? Bot is currently in pre-release, so don't expect things to be totally stable yet.

----

### Acknowledgements

This bot makes use of [Discord4J](https://github.com/Discord4J/Discord4J) to make it easier to use Discord's APIs from Java.

Many of the features of the game this bot implements are heavily based on another card-collection bot from Discord called "Vita". There is very little information out there about this bot online at the moment (it shut down sometime in Spring 2021), and as such there isn't really anything to link here. Sorry!

----

# Running the Bot

### Prerequisites
- Java (version 11.0.2 or higher)
- A Discord application with a bot user
- An open port and an (ideally static) IP address or domain name (reverse proxies such as nginx work too)
- [OPTIONAL] A zkrAuth server (for external access to the web server, currently unreleased)

### Run using a command-line argument
```bash
java -jar DropsBot.jar YOUR_DATABASE_FILE.db
```

### Run using a config file
Put the database file path into "drops-db-path.cfg"
```bash
java -jar DropsBot.jar
```

### Settings to change (see Using the Web Server)
1. Auth Handler public URL
2. Public URL
3. Card Images Folder
4. Bot Client ID
5. Bot Token

# Using the web server
By default, the web server will be accessible via http://127.0.0.1:28002.

When accessing the web server via **any** IP address other than `127.0.0.1`, you will need to authenticate through a zkrAuth server. Note that this includes other commonly-used aliases for the local machine such as `localhost`.

Once you are at the web server, you will see a navigation bar on the left with the following pages.

### All Cards
You can add / edit cards on this page.
Click on a card to edit it, and add additional information (to be used in dungeons et-al) as desired.
Note that cards cannot currently be removed.
### Card Packs
You can add / remove card packs on this page.
Note that card packs cannot be removed if they contain any cards.
Note that card packs cannot currently be edited.
### Info Fields
You can add / edit / remove fields that store extra information on cards here.
Note that you need to set up these fields here before you can add values to them for cards on the All Cards page.
Note that info fields cannot be removed if they are used in any cards.
### General Settings
You can configure settings to control the application, the web server, and the discord bot here.
### Add to Server
If you have your bot's client id set correctly in the General Settings page, this will link to a form on Discord's site allowing you to add the bot to a Discord server you have admin permissions on.
### My Account
On this page you can see how you are currently authenticated, and log out if desired.

# Using the Discord Bot
Invite the bot to your Discord server (this can be done easily via the web server).

Type `,help` (replacing `,` with whatever prefix you have set in the web server) to get a list of commands from the bot.

# Development

### Trello Board
https://trello.com/b/GvQsiItV/drops-bot

### Prerequisites
- Gradle (version 7.2 or higher)

### Building
```bash
gradle build shadowJar
```

### Running using Gradle
```bash
gradle run --args=YOUR_DATABASE_FILE.db
```

### Running using the jar file
Run the jar file at `build/libs/DropsBot.jar` using the instructions from the "Running the Bot" section above -- this is the same jar file distributed in releases.


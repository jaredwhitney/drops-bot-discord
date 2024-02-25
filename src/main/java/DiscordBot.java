import discord4j.core.*;
import discord4j.core.object.entity.*;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.channel.*;
import discord4j.rest.util.*;
import discord4j.core.object.reaction.*;
import discord4j.core.object.presence.*;
import reactor.core.Disposable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import rip.$0k.utils.SysUtils;
import rip.$0k.utils.TimeConstants;
import rip.$0k.http.PermissionIdentifier;

class DiscordBot
{
	private final Random rand = new Random();
	
	private DatabaseManager dm;
	private WebServer ws;
	
	DiscordClient client;
	GatewayDiscordClient gateway;
	Disposable messageListener, reactionListener;
	private Map<User,PendingDropInfo> pendingDropInfo = new HashMap<User,PendingDropInfo>();
	private Map<User,PendingDungeonInfo> pendingDungeonInfo = new HashMap<User,PendingDungeonInfo>();
	
	public DiscordBot(DatabaseManager databaseManager, WebServer webServer)
	{
		dm = databaseManager;
		ws = webServer;
	}
	
	public void start()
	{
		start(null);
	}
	public void start(Runnable onFullRestart)
	{
		try
		{
			if (dm.settings.botToken.length() == 0)
			{
				DropsBot.setSystemTrayNoBot();
				DropsBot.notifyNoBotToken();
				throw new RuntimeException("No bot token has been set; please do so in the web UI at " + dm.settings.siteUrl + "/admin/settings");
			}
			client = DiscordClient.create(dm.settings.botToken);
			gateway = client.login().block();
			
			messageListener = gateway.on(MessageCreateEvent.class).subscribe(event -> {
				try
				{
					final Message message = event.getMessage();
					final String messageContent = message.getContent().trim();
					if (!messageContent.toUpperCase().startsWith(dm.settings.botPrefix.toUpperCase()))
						return;
					final String command = messageContent.substring(dm.settings.botPrefix.length()).split(" ")[0].trim();
					final String messageArgs = messageContent.substring(dm.settings.botPrefix.length()+command.length()).trim();
					final GuildMessageChannel discordChannelObj = (GuildMessageChannel)message.getChannel().block();
					final var discordUserObj = message.getAuthor().orElseThrow();
					final var discordGuildObj = discordChannelObj.getGuild().block().getId();
					final var discordMemberObj = discordUserObj.asMember(discordGuildObj).block();
					final String discordUserId = discordUserObj.getId().asString();
					final PermissionIdentifier PERMISSION_THIS_ACCT_LINKED = ws.auth.getPermissionIdentifier("discord/" + discordUserId);
					final String nickname = discordMemberObj.getDisplayName();
					User user = dm.users.get(discordUserId);
					if (user == null)
					{
						try
						{
							user = new User(dm);
							user.userId = discordUserId;
							user.handleAdd();
						}
						catch (Exception ex)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I ran into an internal error trying to create your user!\n" + ex.getMessage()).subscribe();
							return;
						}
					}
					if ("help".equalsIgnoreCase(command))
					{
						String mergeCommand = "merge";
						for (int i = 0; i < dm.settings.cardsNeededToMerge; i++)
							mergeCommand += " [cardId" + (i+1) + "]";
						String fuseCommand = "fuse";
						for (int i = 0; i < dm.settings.cardsNeededToFuse; i++)
							fuseCommand += " [cardId" + (i+1) + "]";
						discordChannelObj.createMessage(
							(
								"Commands:"
								+ "\n  ~drop - Choose a card of unknown rarity to keep."
								+ "\n  ~inventory - View your inventory."
								+ "\n  ~view [cardId] - View a specific card."
								+ "\n  ~dungeon - Answer trivia to keep a card of unknown rarity."
								+ "\n  ~cooldown - View your cooldown times."
								+ "\n  ~train [cardId] - Train a card to increase its level."
								+ "\n  ~" + mergeCommand + " - Merge " + dm.settings.cardsNeededToMerge + " of a card with the same star level (at least one of them level 100) to get a copy with a higher star level."
								+ "\n  ~favorite [cardId] - Mark a card as favorited."
								+ "\n  ~unfavorite [cardId] - Unmark a card as favorited."
								+ "\n  ~fuse [1s|2s|...] - Fuse your non-favorited cards of a given star level to get cards of a higher star level."
								+ "\n  ~" + fuseCommand + " - Fuse " + dm.settings.cardsNeededToFuse + " cards with the same star level to get a card of a higher star level." 
								+ "\n  ~linkme (account) - Link your discord account to your zkrAuth account."
								+ "\n  ~help - View this help message."
								+ "\nAdmin Commands:"
								+ "\n  ~restart - Restart the bot."
								+ "\n  ~setprefix [prefix] - Set the bot's command prefix."
							).replaceAll("\\~", dm.settings.botPrefix)).block();
					}
					if ("drop".equalsIgnoreCase(command) || "dr".equalsIgnoreCase(command))
					{
						if (System.currentTimeMillis()-user.lastDropTime < dm.settings.dropCooldownMillis)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to wait another " + formatDuration(dm.settings.dropCooldownMillis-(System.currentTimeMillis()-user.lastDropTime)) + " to drop :(").subscribe();
							return;
						}
						user.lastDropTime = System.currentTimeMillis();
						user.handleUpdate(null);
						
						int numCardsForDrop = dm.settings.dropNumCards;
						
						CardDef[] cards = dm.cardDefinitions.values().toArray(CardDef[]::new);
						if (cards.length == 0)
						{
							discordChannelObj.createMessage("Sorry " + nickname + ", I don't have any cards to drop yet!\nAsk an admin to add some using the admin panel at " + dm.settings.siteUrl + "/admin/cards").subscribe();
							return;
						}
						
						String cardStrSend = "";
						ArrayList<CardDef> cardSend = new ArrayList<CardDef>();
						for (int i = 0; i < dm.settings.dropNumCards; i++)
						{
							CardDef cardDef = randomCard(cards);
							cardStrSend += (i>0?"+":"") + cardDef.imageFilename;
							cardSend.add(cardDef);
						}
						final String cardStrSendFinal = cardStrSend;
						
						PendingDropInfo dropInfo = new PendingDropInfo();
						dropInfo.user = user;
						dropInfo.nickname = nickname;
						dropInfo.channel = discordChannelObj;
						dropInfo.cards = cardSend;
						dropInfo.cacheKey = cardStrSendFinal;
						
						try
						{
							BufferedImage combined = Utils.stitchImages(dm, cardSend);
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							ImageIO.write(combined, "webp", baos);
							
							RenderedImageStorage cachedImage = new RenderedImageStorage();
							cachedImage.cachedData = baos.toByteArray();
							ws.renderedImageCache.put(cardStrSendFinal, cachedImage);
						}
						catch (Exception ex){ex.printStackTrace();}
						discordChannelObj.createEmbed(spec -> {
							var temp = spec.setColor(Color.RED)
							.setThumbnail(discordUserObj.getAvatarUrl())
							.setTitle("Drops for " + nickname + ":")
							.setDescription("Pick a card to keep:");
							for (int i = 0; i < numCardsForDrop; i++)
							{
								temp = temp.addField("Card " + (i+1), cardSend.get(i).displayName, true);
							}
							temp.setImage(dm.settings.siteUrl + "/cardpack?" + cardStrSendFinal)
							.setFooter("drops?", dm.settings.siteUrl + "/img/botprofile.png")
							.setTimestamp(Instant.now());
						}).flatMap(msg -> {
							dropInfo.messageId = ((Message)msg).getId().asString();
							pendingDropInfo.put(dropInfo.user, dropInfo);
							var temp = msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93})));
							for (int i = 1; i < numCardsForDrop; i++)
								temp = temp.then(msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}))));
							return temp;
						}).subscribe();
					}
					if ("inv".equalsIgnoreCase(command) || "inventory".equalsIgnoreCase(command))
					{
						String cardsStr = "";
						for (CardInst card : user.inventory.values())
							cardsStr += " - " + (card.favorited?"💖":"") + card.def.displayName + " [" + card.id + "] (" + card.stars + " stars, level " + card.level + ")\n";
						discordChannelObj.createMessage(nickname + "'s Inventory:\n" + cardsStr).block();
					}
					if ("view".equalsIgnoreCase(command) || "v".equalsIgnoreCase(command))
					{
						if (messageArgs.split(" ").length != 1)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to call view with a single card ID :(").subscribe();
							return;
						}
						String id = messageArgs.split(" ")[0].trim();
						CardInst card = dm.cardInstances.get(id);
						if (card != null)
						{
							discordChannelObj.createEmbed(spec ->
								spec.setTitle(card.def.displayName)
								.setDescription(card.def.displayDescription)
								.addField("ID", card.id, true)
								.addField("Stars", card.stars+"", true)
								.addField("Level", card.level+"", true)
								.addField("Favorited", card.favorited+"", true)
								.setImage(dm.settings.siteUrl + "/cardinst/" + card.id + "?nonce=" + System.currentTimeMillis())
								.setFooter("drops?", dm.settings.siteUrl + "/img/botprofile.png")
								.setTimestamp(Instant.now())
							).subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + id + "\" :(").subscribe();
						}
					}
					if ("favorite".equalsIgnoreCase(command) || "fav".equalsIgnoreCase(command))
					{
						if (messageArgs.split(" ").length != 1)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to call favorite on a single card ID :(").subscribe();
							return;
						}
						String id = messageArgs.split(" ")[0].trim();
						CardInst card = dm.cardInstances.get(id);
						if (card != null)
						{
							if (card.owner != user)
							{
								discordChannelObj.createMessage("Sorry " + nickname + ", you don't own that card!").subscribe();
								return;
							}
							var oldCard = card.clone();
							card.favorited = true;
							card.handleUpdate(oldCard);
							discordChannelObj.createMessage("Success! Your card \"" + card.id + "\" has been favorited.").subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + id + "\" :(").subscribe();
						}
					}
					if ("unfavorite".equalsIgnoreCase(command) || "nfav".equalsIgnoreCase(command))
					{
						if (messageArgs.split(" ").length != 1)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to call unfavorite on a single card ID :(").subscribe();
							return;
						}
						String id = messageArgs.split(" ")[0].trim();
						CardInst card = dm.cardInstances.get(id);
						if (card != null)
						{
							if (card.owner != user)
							{
								discordChannelObj.createMessage("Sorry " + nickname + ", you don't own that card!").subscribe();
								return;
							}
							var oldCard = card.clone();
							card.favorited = false;
							card.handleUpdate(oldCard);
							discordChannelObj.createMessage("Success! Your card \"" + card.id + "\" has been favorited.").subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + id + "\" :(").subscribe();
						}
					}
					if ("linkme".equalsIgnoreCase(command))
					{
						if (messageArgs.split(" ").length != 1)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to call linkme on a single zkrAuth username (or no arguments) :(").subscribe();
							return;
						}
						String username = messageArgs.split(" ")[0].trim();
						if (username.length() == 0)
						{
							String[] linkedUsers = ws.auth.listPermissionUsers(PERMISSION_THIS_ACCT_LINKED);
							if (linkedUsers == null)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", something went wrong with your request :(").subscribe();
								return;
							}
							String acctsString = "zkrAuth accounts linked to " + nickname + ":\n";
							for (String linkedUser : linkedUsers)
								acctsString += "\t" + linkedUser + (ws.auth.checkPermission(ws.ADMIN_PERMISSION, linkedUser) ? " [ADMIN]" : "") + "\n";
							discordChannelObj.createMessage(acctsString).subscribe();
						}
						else if (ws.auth.checkPermission(PERMISSION_THIS_ACCT_LINKED, username))
						{
							boolean okay = ws.auth.revokePermission(PERMISSION_THIS_ACCT_LINKED, username);
							if (!okay)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", something went wrong with your request :(").subscribe();
								return;
							}
							else
							{
								discordChannelObj.createMessage("Successfully unlinked discord user \"" + nickname + "\" from zkrAuth user \"" + username + "\".").subscribe();
								return;
							}
						}
						else
						{
							if (!username.contains("@")) {
								username = username + "@" + dm.settings.authHandler;
							}
							PendingLinkInfo pendingLinkInfo = new PendingLinkInfo(discordChannelObj, username, discordUserObj.getUsername(), discordUserObj.getDiscriminator(), discordUserObj.getAvatarUrl(), PERMISSION_THIS_ACCT_LINKED);
							ws.pendingLinkInfo.put(discordUserId, pendingLinkInfo);
							discordChannelObj.createMessage("Hi " + nickname + ", please log in to " + dm.settings.siteUrl + "/linkme?name=" + discordUserId + " as " + username + " to finish linking your account.").subscribe();
							return;
						}
					}
					if ("restart".equalsIgnoreCase(command) || "setprefix".equalsIgnoreCase(command))
					{
						String[] linkedAdminAccounts = getLinkedAdminAccounts(PERMISSION_THIS_ACCT_LINKED);
						if (linkedAdminAccounts == null)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", something went wrong with your request :(").subscribe();
							return;
						}
						if (linkedAdminAccounts.length == 0)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to be linked to a zkrAuth with admin permissions for this drops? Bot instance to do that :(").subscribe();
							return;
						}
						String adminSig = "<@" + discordUserId + "> (" + linkedAdminAccounts[0] + ")";
						if ("restart".equalsIgnoreCase(command))
						{
							discordChannelObj.createMessage("Restarting as requested by " + adminSig + "...").subscribe();
							new Thread(()->{DropsBot.restart(()->{
								discordChannelObj.createMessage("<@" + discordUserId + "> The bot is back online.").subscribe();
							});}).start();
						}
						else if ("setprefix".equalsIgnoreCase(command))
						{
							Settings newSettings = dm.settings.clone();
							newSettings.botPrefix = messageArgs.trim();
							newSettings.handleUpdate(dm.settings);
							dm.settings = newSettings;
							discordChannelObj.createMessage("Prefix changed to `" + dm.settings.botPrefix + "` as requested by " + adminSig).subscribe();;
						}
					}
					if ("fuse".equalsIgnoreCase(command))
					{
						int fuseCardsNeeded = dm.settings.cardsNeededToFuse;
						String[] args = messageArgs.split(" ");
						if (args.length == 1 && "1s".equals(args[0]) || "2s".equals(args[0]) || "3s".equals(args[0]) || "4s".equals(args[0]))
						{
							ArrayList<CardInst> cardOptns = new ArrayList<CardInst>();
							int nstars = args[0].charAt(0)-'0';
							for (CardInst card : user.inventory.values())
								if (card.stars == nstars && !card.favorited)
									cardOptns.add(card);
							int numNewCards = cardOptns.size() / fuseCardsNeeded;
							if (numNewCards == 0)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", you need at least " + fuseCardsNeeded + " unfavorited " + nstars + "-star cards to do that. You only have " + cardOptns.size() + ". :(").subscribe();
								return;
							}
							for (int i = 0; i < numNewCards; i++)
							{
								CardInst newCard = genCard();
								newCard.owner = user;
								newCard.stars = nstars+1;
								newCard.handleAdd();
								discordChannelObj.createMessage("Fuse success [" + (i+1) + "/" + numNewCards + "]! Enjoy your new " + newCard.stars + " star " + newCard.def.displayName + " (level " + newCard.level + ") [id: " + newCard.id + "]").subscribe();
							}
							for (int i = 0; i < numNewCards * fuseCardsNeeded; i++)
							{
								CardInst card = cardOptns.get(i);
								card.handleRemove();
							}
							return;
						}
						if (args.length != fuseCardsNeeded)
						{
							String msg = "Wrong number of cards! You need to call the command like this:\n" + dm.settings.botPrefix + "fuse";
							for (int i = 0; i < fuseCardsNeeded; i++)
								msg += " [cardId" + (i+1) + "]";
							discordChannelObj.createMessage(msg).subscribe();
							return;
						}
						CardInst[] cards = new CardInst[args.length];
						for (int i = 0; i < args.length; i++)
						{
							cards[i] = dm.cardInstances.get(args[i]);
							if (cards[i] == null)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + args[i] + "\" :(").subscribe();
								return;
							}
							for (int j = 0; j < i; j++)
							{
								if (cards[i].id == cards[j].id)
								{
									discordChannelObj.createMessage("Sorry " + nickname +", you can't pass the same card to fuse more than once (triggered by card \"" + args[i] + "\") :(").subscribe();
									return;
								}
							}
							if (cards[i].owner != user)
							{
								discordChannelObj.createMessage("Sorry " + nickname + ", you don't own card \"" + args[i] + "\"!").subscribe();
								return;
							}
							if (cards[i].stars != cards[0].stars)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", the cards you passed to fuse weren't all at the same star level :(").subscribe();
								return;
							}
						}
						CardInst newCard = genCard();
						newCard.owner = user;
						newCard.stars = cards[0].stars + 1;
						newCard.handleAdd();
						for (CardInst card : cards)
							card.handleRemove();
						discordChannelObj.createMessage("Fuse success! Enjoy your new " + newCard.stars + " star " + newCard.def.displayName + " (level " + newCard.level + ") [id: " + newCard.id + "]").subscribe();
					}
					if ("train".equalsIgnoreCase(command) || "tr".equalsIgnoreCase(command))
					{
						if (messageArgs.split(" ").length != 1)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to call train with a single card ID :(").subscribe();
							return;
						}
						String id = messageArgs.split(" ")[0].trim();
						CardInst card = dm.cardInstances.get(id);
						if (card != null)
						{
							if (card.owner != user)
							{
								discordChannelObj.createMessage("Sorry " + nickname + ", you don't own that card!").subscribe();
								return;
							}
							if (System.currentTimeMillis()-user.lastTrainTime < dm.settings.trainCooldownMillis)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", you need to wait another " + formatDuration(dm.settings.trainCooldownMillis-(System.currentTimeMillis()-user.lastTrainTime)) + " to train a card :(").subscribe();
								return;
							}
							user.lastTrainTime = System.currentTimeMillis();
							user.handleUpdate(null);
							
							var oldCard = card.clone();
							
							int gain = (int)(Math.random()*4)+1;
							card.level = card.level+gain;
							
							card.handleUpdate(oldCard);
							
							discordChannelObj.createEmbed(spec ->
								spec.setTitle("Training Results for " + card.def.displayName)
								.addField("Level", card.level+" (+" + gain + ")", true)
								.setImage(dm.settings.siteUrl + "/cardinst/" + card.id + "?nonce=" + System.currentTimeMillis())
								.setFooter("drops?", dm.settings.siteUrl + "/img/botprofile.png")
								.setTimestamp(Instant.now())
							).subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + id + "\" :(").subscribe();
						}
					}
					if ("merge".equalsIgnoreCase(command) || "m".equalsIgnoreCase(command))
					{
						if (messageArgs.split(" ").length != dm.settings.cardsNeededToMerge)
						{
							String msg = "Wrong number of cards! You need to call the command like this:\n" + dm.settings.botPrefix + "merge";
							for (int i = 0; i < dm.settings.cardsNeededToMerge; i++)
								msg += " [cardId" + (i+1) + "]";
							discordChannelObj.createMessage(msg).subscribe();
							return;
						}
						String[] pieces = messageArgs.split(" ");
						CardInst[] cards = new CardInst[pieces.length];
						boolean cardMaxxed = false;
						for (int i = 0; i < pieces.length; i++)
						{
							cards[i] = dm.cardInstances.get(pieces[i]);
							if (cards[i] == null)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + pieces[i] + "\" :(").subscribe();
								return;
							}
							for (int j = 0; j < i; j++)
							{
								if (cards[i].id == cards[j].id)
								{
									discordChannelObj.createMessage("Sorry " + nickname +", you can't pass the same card to merge more than once (triggered by card \"" + pieces[i] + "\") :(").subscribe();
									return;
								}
							}
							if (cards[i].owner != user)
							{
								discordChannelObj.createMessage("Sorry " + nickname + ", you don't own card \"" + pieces[i] + "\"!").subscribe();
								return;
							}
							if (cards[i].level >= 100)
								cardMaxxed = true;
							if (cards[i].stars != cards[0].stars)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", the cards you passed to merge weren't all at the same star level :(").subscribe();
								return;
							}
							if (cards[i].def != cards[0].def)
							{
								discordChannelObj.createMessage("Sorry " + nickname +", the cards you passed to merge weren't all for the same card definition :(").subscribe();
								return;
							}
						}
						if (!cardMaxxed)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", at least one of the cards you're merging has to be level 100 :(").subscribe();
							return;
						}
						CardInst newCard = genCard(cards[0].def);
						newCard.owner = user;
						newCard.stars = cards[0].stars + 1;
						newCard.handleAdd();
						for (CardInst card : cards)
							card.handleRemove();
						discordChannelObj.createMessage("Merge success! Enjoy your new " + newCard.stars + " star " + newCard.def.displayName + " (level " + newCard.level + ") [id: " + newCard.id + "]").subscribe();
					}
					if ("dg".equalsIgnoreCase(command) || "dungeon".equalsIgnoreCase(command))
					{
						if (System.currentTimeMillis()-user.lastDungeonTime < dm.settings.dungeonCooldownMillis)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to wait another " + formatDuration(dm.settings.dungeonCooldownMillis-(System.currentTimeMillis()-user.lastDungeonTime)) + " to attempt a dungeon :(").subscribe();
							return;
						}
						user.lastDungeonTime = System.currentTimeMillis();
						user.handleUpdate(null);
						
						// If the settings value changes in the middle of this, we don't want it to break things
						final int numOptionsForDungeon = dm.settings.dungeonOptions;
						
						HashMap<CardInfoField, ArrayList<String>> dungeonFieldOptions = new HashMap<CardInfoField, ArrayList<String>>();
						for (CardInfoField field : dm.cardInfoFields.values())
						{
							ArrayList<String> fieldUniqueEntries = new ArrayList<String>();
							HashMap<String,Boolean> uniqueStore = new HashMap<String,Boolean>();
							for (CardInfoFieldEntry entry : field.entries.values())
							{
								if (uniqueStore.get(entry.value) == null)
								{
									fieldUniqueEntries.add(entry.value);
									uniqueStore.put(entry.value, true);
								}
							}
							if (fieldUniqueEntries.size() >= numOptionsForDungeon)
								dungeonFieldOptions.put(field, fieldUniqueEntries);
						}
						
						if (dungeonFieldOptions.size() > 0)
						{
							CardInfoField dungeonField = dungeonFieldOptions.keySet().toArray(CardInfoField[]::new)[(int)(Math.random()*dungeonFieldOptions.size())];
							ArrayList<String> dungeonFieldUniqueValues = dungeonFieldOptions.get(dungeonField);
							
							ArrayList<CardDef> cardOptions = new ArrayList<CardDef>();
							for (CardDef card : dm.cardDefinitions.values())
								if (card.info.get(dungeonField) != null && card.info.get(dungeonField).size() > 0)
									cardOptions.add(card);
							CardDef dungeonCard = randomCard(cardOptions);
							
							ArrayList<CardInfoFieldEntry> possibleCorrectEntries = dungeonCard.info.get(dungeonField);
							CardInfoFieldEntry correctEntry = possibleCorrectEntries.get((int)(Math.random()*possibleCorrectEntries.size()));
							
							String[] dungeonValues = new String[numOptionsForDungeon];
							int correctEntryIndex = -1;
							Collections.shuffle(dungeonFieldUniqueValues);
							for (int i = 0; i < dungeonValues.length; i++)
							{
								dungeonValues[i] = dungeonFieldUniqueValues.get(i);
								if (dungeonValues[i].equals(correctEntry.value))
									correctEntryIndex = i;
							}
							if (correctEntryIndex < 0)
							{
								correctEntryIndex = (int)(Math.random()*dungeonValues.length);
								dungeonValues[correctEntryIndex] = correctEntry.value;
							}
							
							PendingDungeonInfo dungeonInfo = new PendingDungeonInfo();
							dungeonInfo.user = user;
							dungeonInfo.nickname = nickname;
							dungeonInfo.channel = discordChannelObj;
							dungeonInfo.correctEntryIndex = correctEntryIndex;
							dungeonInfo.card = dungeonCard;
							
							System.out.println("Dungeon correctEntryIndex is " + correctEntryIndex);
							
							discordChannelObj.createEmbed(spec -> {
								var temp = spec.setColor(Color.RED)
								.setThumbnail(message.getAuthor().orElseThrow(null).getAvatarUrl())
								.setTitle("Dungeon for " + nickname + ":")
								.setDescription(dungeonCard.displayName + " - " + dungeonField.questionFormat);
								for (int i = 0; i < dungeonValues.length; i++)
								{
									temp = temp.addField(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}), dungeonValues[i], true);
								}
								temp.setImage(dm.settings.siteUrl + "/card/" + dungeonCard.imageFilename)
								.setFooter("drops?", dm.settings.siteUrl + "/img/botprofile.png")
								.setTimestamp(Instant.now());
							}).flatMap(msg -> {
								dungeonInfo.messageId = ((Message)msg).getId().asString();
								pendingDungeonInfo.put(dungeonInfo.user, dungeonInfo);
								var temp = msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93})));
								for (int i = 1; i < dungeonValues.length; i++)
									temp = temp.then(msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}))));
								return temp;
							}).subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname + ", I don't have enough card info to create dungeons with the specified option count of " + dm.settings.dungeonOptions + ".\nAsk an admin to add more card info using the admin panel at " + dm.settings.siteUrl + "/admin/cards or lower the dungeon option count.").subscribe();
						}
						
					}
					if ("cd".equalsIgnoreCase(command) || "cooldown".equalsIgnoreCase(command))
					{
						long dropCd = dm.settings.dropCooldownMillis - (System.currentTimeMillis() - user.lastDropTime);
						long dungeonCd = dm.settings.dungeonCooldownMillis - (System.currentTimeMillis() - user.lastDungeonTime);
						long trainCd = dm.settings.trainCooldownMillis - (System.currentTimeMillis() - user.lastTrainTime);
						discordChannelObj.createEmbed(spec ->
							spec.setTitle("Cooldowns")
								.setDescription("for " + nickname)
								.setThumbnail(discordUserObj.getAvatarUrl())
								.addField("Drop", (dropCd <= 0 ? "[READY]" : formatDuration(dropCd)), false)
								.addField("Dungeon", (dungeonCd <= 0 ? "[READY]" : formatDuration(dungeonCd)), true)
								.addField("Train", (trainCd <= 0 ? "[READY]" : formatDuration(trainCd)), true)
								.setFooter("drops?", dm.settings.siteUrl + "/img/botprofile.png")
								.setTimestamp(Instant.now())
						).subscribe();
					}
				}
				catch (Exception ex)
				{
					System.out.println("Uh-oh! Unhandled exception in the Discord bot code.\nNote: Both the bot and the web-server will continue running.");
					ex.printStackTrace();
				}
			});
			
			reactionListener = gateway.getEventDispatcher().on(ReactionAddEvent.class).subscribe(
				event ->
				{
					try
					{
						try {
							event.getEmoji().asUnicodeEmoji().orElseThrow();
						} catch (Exception ex) {
							return;
						}
						
						String discordMessageId = event.getMessageId().asString();
						String discordUserId = event.getUserId().asString();
						User user = dm.users.get(discordUserId);
						if (user == null)
							return;
						
						PendingDropInfo dropInfo = pendingDropInfo.get(user);
						PendingDungeonInfo dungeonInfo = pendingDungeonInfo.get(user);
						
						if (dropInfo != null && dropInfo.messageId.equals(discordMessageId))
						{
							CardDef selectedCard = null;
							
							String raw = event.getEmoji().asUnicodeEmoji().orElseThrow().getRaw();
							byte[] b = raw.getBytes();
							if (b.length == 7 && b[1] == (byte)-17 && b[2] == (byte)-72 && b[3] == (byte)-113 && b[4] == (byte)-30 && b[5] == (byte)-125 && b[6] == (byte)-93)
							{
								int ind = raw.charAt(0)-0x31;
								if (ind >= 0 && ind < dropInfo.cards.size())
									selectedCard = dropInfo.cards.get(ind);
							}
							if (selectedCard != null)
							{
								try
								{
									CardInst card = genCard(selectedCard);
									card.owner = user;
									card.handleAdd();
									dropInfo.channel.createMessage("Enjoy your new " + card.stars + " star " + card.def.displayName + " (level " + card.level + ") [id: " + card.id + "]").subscribe();
									pendingDropInfo.remove(dropInfo);
								}
								catch (SQLException ex)
								{
									dropInfo.channel.createMessage("Sorry " + dropInfo.nickname + ", the server encountered an error while processing your request.\n" + ex.getMessage()).subscribe();
								}
								ws.renderedImageCache.remove(dropInfo.cacheKey);
							}
						}
						else if (dungeonInfo != null && dungeonInfo.messageId.equals(discordMessageId))
						{
							String raw = event.getEmoji().asUnicodeEmoji().orElseThrow().getRaw();
							byte[] b = raw.getBytes();
							if (b.length == 7 && b[1] == (byte)-17 && b[2] == (byte)-72 && b[3] == (byte)-113 && b[4] == (byte)-30 && b[5] == (byte)-125 && b[6] == (byte)-93)
							{
								pendingDungeonInfo.remove(dungeonInfo);
								int ind = raw.charAt(0)-0x31;
								System.out.println(dungeonInfo.nickname + " selected dungeon entry index " + ind);
								if (ind == dungeonInfo.correctEntryIndex)
								{
									try
									{
										CardInst card = genCard(dungeonInfo.card);
										card.owner = user;
										card.handleAdd();
										dungeonInfo.channel.createMessage("Enjoy your new " + card.stars + " star " + card.def.displayName + " (level " + card.level + ") [id: " + card.id + "]").subscribe();
									}
									catch (SQLException ex)
									{
										ex.printStackTrace();
										event.getChannel().block().createMessage("Sorry " + dungeonInfo.nickname + ", the server encountered an error while processing your request.\n" + ex.getMessage()).subscribe();
									}
								}
								else
								{
									dungeonInfo.channel.createMessage("Sorry " + dungeonInfo.nickname + ", that was wrong.\nBetter luck next time <3").subscribe();
								}
							}
						}
					}
					catch (Exception ex)
					{
						System.out.println("Uh-oh! Unhandled exception in the Discord bot code.\nNote: Both the bot and the web-server will continue running.");
						ex.printStackTrace();
					}
				}
			);
			updateBotStatus(dm.settings);
			System.out.println("The Discord bot should be up and running!");
			if (onFullRestart != null)
				onFullRestart.run();
		}
		catch (Exception ex)
		{
			System.out.println("Uh-oh! Unhandled exception in the Discord bot code.\n\tbotToken: " + dm.settings.botToken);
			ex.printStackTrace();
			System.out.println("Note: The web-server will continue running.");
			DropsBot.setSystemTrayNoBot();
		}
	}
	
	public void updateBotStatus(Settings settings)
	{
		try
		{
			gateway.updatePresence(Presence.online(Activity.listening(settings.botPrefix + "help"))).subscribe();
		}
		catch (Exception ex) {}
	}
	
	public String[] getLinkedAdminAccounts(PermissionIdentifier linkPermission)
	{
		String[] linkedUsers = ws.auth.listPermissionUsers(linkPermission);
		if (linkedUsers == null)
			return null;
		String adminUsername = null;
		ArrayList<String> adminUsers = new ArrayList<String>();
		for (String linkedUser : linkedUsers)
		{
			if (ws.auth.checkPermission(ws.ADMIN_PERMISSION, linkedUser))
			{
				adminUsers.add(linkedUser);
			}
		}
		return adminUsers.toArray(String[]::new);
	}
	
	public void shutdown()
	{
		try
		{
			gateway.logout();
		}
		catch (Exception ex)
		{}
		try
		{
			messageListener.dispose();
		}
		catch (Exception ex)
		{}
		try
		{
			reactionListener.dispose();
		}
		catch (Exception ex)
		{}
	}
	
	public CardDef randomCard(CardDef[] optns)
	{
		RandomCollection<CardDef> col = new RandomCollection<CardDef>(rand);
		for (CardDef def : optns)
			col.add(def.cardRarityMultiplier, def);
		return col.next();
	}
	
	public CardDef randomCard(Collection<CardDef> optns)
	{
		RandomCollection<CardDef> col = new RandomCollection<CardDef>(rand);
		for (CardDef def : optns)
			col.add(def.cardRarityMultiplier, def);
		return col.next();
	}
	
	public CardInst genCard()
	{
		CardDef def = randomCard(dm.cardDefinitions.values());
		return genCard(def);
	}
	
	public CardInst genCard(CardDef cardDef)
	{
		String id = null;
		byte[] idbytes = new byte[4];
		do
		{
			rand.nextBytes(idbytes);
			id = SysUtils.encodeBase64(idbytes).replaceAll("[^a-zA-Z0-9]","").toLowerCase();
		}
		while (dm.cardInstances.get(id) != null);
		CardInst ret = new CardInst(dm);
		ret.def = cardDef;
		ret.id = id;
		ret.level = 1;
		ret.stars = (int)Math.ceil(Math.pow(Math.random(),10)*4);
		ret.owner = null;
		return ret;
	}
	
	public static String formatDuration(long ms)
	{
		if (ms == 0)
			return "0 seconds";
		if (ms < TimeConstants.MILLISECONDS_PER_SECOND)
			return String.format("%.2f seconds", ms/1000.0);
		if (ms < TimeConstants.MILLISECONDS_PER_MINUTE)
		{
			long val = (ms/TimeConstants.MILLISECONDS_PER_SECOND);
			return val + " second" + (val==1?"":"s");
		}
		if (ms < TimeConstants.MILLISECONDS_PER_HOUR)
		{
			long val = (ms/TimeConstants.MILLISECONDS_PER_MINUTE);
			return val + " minute" + (val==1?"":"s");
		}
		long val = (ms/TimeConstants.MILLISECONDS_PER_DAY);
		return val + " day" + (val==1?"":"s");
	}
}

// From https://stackoverflow.com/a/6409791
class RandomCollection<E> {
    private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
    private final Random random;
    private double total = 0;

    public RandomCollection() {
        this(new Random());
    }

    public RandomCollection(Random random) {
        this.random = random;
    }

    public RandomCollection<E> add(double weight, E result) {
        if (weight <= 0) return this;
        total += weight;
        map.put(total, result);
        return this;
    }

    public E next() {
        double value = random.nextDouble() * total;
        return map.higherEntry(value).getValue();
    }
}
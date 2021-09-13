import discord4j.core.*;
import discord4j.core.object.entity.*;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.channel.*;
import discord4j.rest.util.*;
import discord4j.core.object.reaction.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import rip.$0k.utils.SysUtils;
import rip.$0k.utils.TimeConstants;

class DiscordBot
{
	private final Random rand = new Random();
	
	private DatabaseManager dm;
	private WebServer ws;
	
	GatewayDiscordClient gateway;
	private Map<User,PendingDropInfo> pendingDropInfo = new HashMap<User,PendingDropInfo>();
	private Map<User,PendingDungeonInfo> pendingDungeonInfo = new HashMap<User,PendingDungeonInfo>();
	
	public DiscordBot(DatabaseManager databaseManager, WebServer webServer)
	{
		dm = databaseManager;
		ws = webServer;
	}
	
	public void start()
	{
		try
		{
			if (dm.settings.botToken.length() == 0)
			{
				DropsBot.setSystemTrayNoBot();
				DropsBot.notifyNoBotToken();
				throw new RuntimeException("No bot token has been set; please do so in the web UI at " + dm.settings.siteUrl + "/admin/settings");
			}
			final DiscordClient client = DiscordClient.create(dm.settings.botToken);
			gateway = client.login().block();
			
			gateway.on(MessageCreateEvent.class).subscribe(event -> {
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
					final String nickname = discordMemberObj.getDisplayName();
					User user = dm.users.get(discordUserId);
					if (user == null)
					{
						try
						{
							user = new User(dm);
							user.userId = discordUserId;
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
						discordChannelObj.createMessage("Commands:\n  ~drop\n  ~inventory\n  ~view [cardId]\n  ~dungeon\n  ~cooldown\n  ~train [cardId]\n~" + mergeCommand + "\n  ~help".replaceAll("\\~", dm.settings.botPrefix)).block();
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
							CardDef cardDef = cards[(int)(Math.random()*cards.length)];
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
							cardsStr += " - " + card.def.displayName + " [" + card.id + "] (" + card.stars + " stars, level " + card.level + ")\n";
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
								.setImage(dm.settings.siteUrl + "/cardinst/" + card.id)
								.setFooter("drops?", dm.settings.siteUrl + "/img/botprofile.png")
								.setTimestamp(Instant.now())
							).subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + id + "\" :(").subscribe();
						}
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
						if (card.owner != user)
						{
							discordChannelObj.createMessage("Sorry " + nickname + ", you don't own that card!").subscribe();
							return;
						}
						if (card != null)
						{
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
								.setImage(dm.settings.siteUrl + "/cardinst/" + card.id)
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
							CardDef dungeonCard = cardOptions.get((int)(Math.random()*cardOptions.size()));
							
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
						discordChannelObj.createEmbed(spec ->
							spec.setTitle("Cooldowns")
								.setDescription("for " + nickname)
								.setThumbnail(discordUserObj.getAvatarUrl())
								.addField("Drop", (dropCd <= 0 ? "[READY]" : formatDuration(dropCd)), false)
								.addField("Dungeon", (dungeonCd <= 0 ? "[READY]" : formatDuration(dungeonCd)), true)
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
			
			gateway.getEventDispatcher().on(ReactionAddEvent.class).subscribe(
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
			System.out.println("The Discord bot should be up and running!");
			gateway.onDisconnect().block();
		}
		catch (Exception ex)
		{
			System.out.println("Uh-oh! Unhandled exception in the Discord bot code.\n\tbotToken: " + dm.settings.botToken);
			ex.printStackTrace();
			System.out.println("Note: The web-server will continue running.");
			DropsBot.setSystemTrayNoBot();
		}
	}
	
	public void shutdown()
	{
		try
		{
			gateway.logout();
		}
		catch (Exception ex)
		{}
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
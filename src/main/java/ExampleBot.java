import discord4j.core.*;
import discord4j.core.object.entity.*;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.channel.*;
import discord4j.rest.util.*;
import discord4j.core.object.reaction.*;
import java.time.Instant;
import rip.$0k.http.*;
import rip.$0k.utils.*;
import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.*;
import java.awt.image.*;
import java.sql.*;

public final class ExampleBot
{
	public static final String DATABASE_LOCATION = "/www/drops.0k.rip/dropdatabase.db";
	
	// populated from SQL database
	public static Settings settings;
	public static Map<String,User> users = new HashMap<String,User>();
	public static Map<String,CardPack> cardPacks = new HashMap<String,CardPack>();
	public static Map<String,CardDef> cardDefinitions = new HashMap<String,CardDef>();
	public static Map<String,CardInst> cardInstances = new HashMap<String,CardInst>();
	public static Map<String,CardInfoField> cardInfoFields = new HashMap<String,CardInfoField>();
	public static Map<String,CardInfoFieldEntry> cardInfoFieldEntries = new HashMap<String,CardInfoFieldEntry>();
	
	// need no population from SQL database
	public static Map<User,PendingDropInfo> pendingDropInfo = new HashMap<User,PendingDropInfo>();
	public static Map<User,PendingDungeonInfo> pendingDungeonInfo = new HashMap<User,PendingDungeonInfo>();
	public static Map<String,RenderedImageStorage> renderedImageCache = new HashMap<String,RenderedImageStorage>();
	public static final Random rand = new Random();
	public static Connection connection;
	public static Statement statement;
	public static String cardHTML;
	
	public static void main(final String[] args) throws SQLException
	{
		
		DatabaseManager.connectToDatabase();
		DatabaseManager.initAllTables();
		DatabaseManager.readAllFromDatabase();
		
		try
		{
			HttpServer server = new HttpServer(settings.serverPort);
			AuthHandler auth = new AuthHandler(settings.authHandler);
			auth.registerCallbackEndpoint("/auth/callback");
			cardHTML = SysUtils.readTextFile(new File("cards.html"));
			server.accept((req) -> {
				if (auth.handle(req, "drops-admin"))
					return;
				if (req.matches(HttpVerb.GET, "/") || req.matches(HttpVerb.GET, "/index.html"))
				{
					req.respond("<body>"
						+ "<a href=\"/admin/settings\">General Settings</a><br>"
						+ "<a href=\"/admin/cardpacks\">View / edit card packs</a><br>"
						+ "<a href=\"/admin/cards\">View / edit cards</a><br>"
						+ "<a href=\"/admin/infofield\">View / edit card extra info field options</a><br>"
						+ "<a href=\"https://discordapp.com/api/oauth2/authorize?client_id=" + settings.botClientId + "&permissions=243336208192&scope=bot\">Add drops bot to a Discord server</a><br>"
					+ "</body>");
					return;
				}
				else if (req.matches(HttpVerb.GET, "/cardpack"))
				{
					try
					{
						String[] packCards = req.parameters.split("\\Q&nonce=\\E")[0].split("\\+");
						System.out.println(req.parameters.split("\\Q&nonce=\\E")[0]);
						byte[] data = renderedImageCache.get(req.parameters.split("\\Q&nonce=\\E")[0]).cachedData;
						if (data == null)
						{
							BufferedImage combined = stitchImages(packCards);
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							ImageIO.write(combined, "webp", baos);
							data = baos.toByteArray();
						}
						System.out.println("Send response");
						req.respond("image/webp", data);
						System.out.println("Sent!");
					}
					catch (Exception ex){
						ex.printStackTrace();
						req.respond(HttpStatus.NOT_FOUND_404);
					}
					return;
				}
				if (!auth.enforceValidCredentials("drops-admin"))
					return;
				if (req.matches(HttpVerb.GET, "/admin/cardpacks"))
				{
					String resp = "<body>";
					resp += "<a href=\"/admin/cardpacks/add\">Create a new cardpack</a><br>";
					resp += "<h1>Card packs</h1>";
					for (CardPack pack : cardPacks.values())
					{
						resp += "<a href=\"/admin/cardpack?name=" + pack.packName + "\">" + pack.packName + " (" + pack.cards.size() + " cards)</a><br>";
					}
					resp += "</body>";
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.GET, "/admin/cardpacks/add"))
				{
					String resp = "<body>"
						+ "<form enctype=\"multipart/form-data\" action=\"/admin/cardpacks/add\" method=\"post\">"
							+ "Cardpack Name: <input name=\"packName\"/><br>"
							+ "<input type=\"submit\" value=\"Create card pack!\">"
						+ "</form>"
						+ "<a href=\"/admin/cardpacks\">Cancel</a>"
						+ "</body>";
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/cardpacks/add"))
				{
					try
					{
						String cardPackName = new String(req.getMultipart("packName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						CardPack cardPack = new CardPack();
						cardPack.packName = cardPackName;
						cardPack.handleAdd();
						cardPacks.put(cardPack.packName, cardPack);
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/cardpack?name=" + cardPackName + "\">/admin/cardpack?name=" + cardPackName + "</a>",
							"Location: /admin/cardpack?name=" + cardPackName
						);
					}
					catch (SQLException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/cardpack"))
				{
					String pack = req.getParam("name")[0];
					String resp = "<body>";
					resp += "<h1>Card pack: " + pack + "</h1>";
					resp += "<a href=\"/admin/cards/add?pack=" + pack + "\">Add a new card to this pack</a><br>";
					resp += "<h2>Pack cards:</h2>";
					for (CardDef card : cardPacks.get(pack).cards.values())
					{
						resp += "<a href-\"/admin/card?name=" + card.imageFilename + "\">" + card.displayName + "</a><br>";
					}
					resp += "</body>";
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.GET, "/admin/cards"))
				{
					String datajson = "var data = {\n";
					for (CardDef card : cardDefinitions.values())
					{
						datajson += "\t\"" + card.imageFilename + "\": {\n";
						datajson += "\t\t\"displayName\": \"" + card.displayName + "\",\n";
						if (card.displayDescription != null)
							datajson += "\t\t\"displayDescription\": \"" + card.displayDescription + "\",\n";
						datajson += "\t\t\"image\": \"https://" + settings.siteUrl + "/card/" + card.imageFilename + "\",\n";
						datajson += "\t\t\"extraInfo\": [\n";
						for (ArrayList<CardInfoFieldEntry> entryList : card.info.values())
						{
							for (CardInfoFieldEntry entry : entryList)
								datajson += "\t\t\t{ \"id\": \"" + entry.id + "\", \"key\": \"" + entry.field.keyName + "\", \"value\": \"" + entry.value + "\" }\n";
						}
						datajson += "\t\t]\n";
						datajson += "},\n";
					}
					datajson += "}\n";
					datajson += "var keys = [\n";
					for (CardInfoField field : cardInfoFields.values())
					{
						datajson += "\t{ \"raw\": \"" + field.keyName + "\", \"display\": \"" + field.questionFormat + "\" },\n";
					}
					datajson += "]\n";
					datajson += "var packs = [\n";
					for (CardPack pack : cardPacks.values())
					{
						datajson += "\t\"" + pack.packName + "\",\n";
					}
					datajson += "]";
					String resp = cardHTML.replaceAll("\\Q<<>>DATALOC<<>>\\E",datajson);
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/cards/add"))
				{
					try
					{
						String displayDescription = new String(req.getMultipart("displayDescription")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String cardPack = new String(req.getMultipart("cardPack")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						HttpRequest.Multipart fileDesc = req.getMultipart("cardImage")[0];
						String rawName = SysUtils.stripDangerousCharacters(fileDesc.filename.substring(0, fileDesc.filename.lastIndexOf("."))) + "." + SysUtils.stripDangerousCharacters(fileDesc.filename.substring(fileDesc.filename.lastIndexOf(".")));
						String displayName = new String(req.getMultipart("displayName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						if (displayName.length() == 0)
							displayName = SysUtils.toTitleCase(rawName);
						
						CardDef card = new CardDef();
						card.imageFilename = rawName;
						card.displayName = displayName;
						card.displayDescription = displayDescription;
						card.cardPack = cardPacks.get(cardPack);
						
						Files.write(Paths.get(settings.cardsFolder, rawName), fileDesc.filedata);
						card.handleAdd();
						cardDefinitions.put(card.imageFilename, card);
						
						String redirectURL = (req.getParam("pack").length==0 ? "/admin/cards?name="+rawName : "/admin/cardpack?name=" + cardPack);
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"" + redirectURL + "\">" + redirectURL + "</a>",
							"Location: " + redirectURL
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/cards/edit"))
				{
					try
					{
						String displayDescription = new String(req.getMultipart("displayDescription")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String cardPack = new String(req.getMultipart("cardPack")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String rawName = new String(req.getMultipart("imageFilename")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String displayName = new String(req.getMultipart("displayName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						if (displayName.length() == 0)
							displayName = SysUtils.toTitleCase(rawName);
						
						CardDef card = cardDefinitions.get(rawName);
						var cardOld = card.clone();
						card.imageFilename = rawName;
						card.displayName = displayName;
						card.displayDescription = displayDescription;
						card.cardPack = cardPacks.get(cardPack);
						
						card.handleUpdate(cardOld);
						
						HashMap<String,Boolean> infoFieldPresent = new HashMap<String,Boolean>();
						for (Map.Entry<String,ArrayList<HttpRequest.Multipart>> multipart : req.multiparts.entrySet())
						{
							String key = multipart.getKey();
							if (key.indexOf("extra-info-key-") > 0)
							{
								String keyId = key.substring("extra-info-key-".length());
								String keyName = new String(multipart.getValue().get(0).filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
								String keyValue = new String(req.multiparts.get("extra-info-value-"+keyId).get(0).filedata, java.nio.charset.StandardCharsets.UTF_8);
								if (keyId.indexOf("NEW_ASSIGN") > 0)
								{
									// Handle adding a new info entry
									CardInfoFieldEntry entry = new CardInfoFieldEntry();
									byte[] idbytes = new byte[16];
									do
									{
										rand.nextBytes(idbytes);
										entry.id = SysUtils.encodeBase64(idbytes).replaceAll("[^a-zA-Z0-9]","").toLowerCase();
									} while (cardInfoFields.get(entry.id) != null);
									entry.card = card;
									entry.field = cardInfoFields.get(keyName);
									entry.value = keyValue;
									
									entry.handleAdd();
									cardInfoFieldEntries.put(entry.id, entry);
									
									infoFieldPresent.put(entry.id, true);
								}
								else
								{
									// Handle updating an existing info entry
									CardInfoFieldEntry entry = cardInfoFieldEntries.get(keyId);
									var entryOld = entry.clone();
									entry.field = cardInfoFields.get(keyName);
									entry.value = keyValue;
									
									entry.handleUpdate(entryOld);
									
									infoFieldPresent.put(entry.id, true);
								}
							}
						}
						for (ArrayList<CardInfoFieldEntry> entryList : card.info.values())
						{
							for (CardInfoFieldEntry entry : entryList)
							{
								if (infoFieldPresent.get(entry.id) == null)
								{
									// Handle removing an info entry
									entry.handleRemove();
									cardInfoFieldEntries.remove(entry.id);
								}
							}
						}
						
						String redirectURL = (req.getParam("pack").length==0 ? "/admin/cards?name="+rawName : "/admin/cardpack?name=" + cardPack);
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"" + redirectURL + "\">" + redirectURL + "</a>",
							"Location: " + redirectURL
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/infofield"))
				{
					try
					{
						String ret = "<body>";
						ret += "<form enctype=\"multipart/form-data\" action=\"/admin/infofield/add\" method=\"post\">Key: <input name=\"keyName\"> QuestionFormat: <input name=\"questionFormat\"><input type=\"submit\" value=\"add entry\"/></form>";
						for (CardInfoField field : cardInfoFields.values())
						{
							String hiddenInputs = "<input type=\"hidden\" value=\"" + field.keyName + "\" name=\"keyName\"/>";
							ret += "<form enctype=\"multipart/form-data\" action=\"/admin/infofield/remove\" method=\"post\"><input type=\"submit\" value=\"remove entry\">" + hiddenInputs + "</form>";
							ret += "Key: " + field.keyName + " <form enctype=\"multipart/form-data\" action=\"/admin/infofield/edit\" method=\"post\">QuestionFormat: <input name=\"questionFormat\" value=\"" + field.questionFormat + "\">" + hiddenInputs + "<input type=\"submit\" value=\"update\"/></form>";
							ret += "<br>";
						}
						ret += "</body>";
						req.respond(ret);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield/add"))
				{
					try
					{
						String keyName = new String(req.getMultipart("keyName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String questionFormat = new String(req.getMultipart("questionFormat")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						
						CardInfoField field = new CardInfoField();
						field.keyName = keyName;
						field.questionFormat = questionFormat;
						
						field.handleAdd();
						cardInfoFields.put(field.keyName, field);
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/infofield\">/admin/infofield</a>",
							"Location: /admin/infofield"
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield/remove"))
				{
					try
					{
						String keyName = new String(req.getMultipart("keyName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						
						CardInfoField field = cardInfoFields.get(keyName);
						
						field.removeFromDatabase();
						field.removeFromObjects();
						cardInfoFields.remove(field);
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/infofield\">/admin/infofield</a>",
							"Location: /admin/infofield"
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield/edit"))
				{
					try
					{
						String keyName = new String(req.getMultipart("keyName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String questionFormat = new String(req.getMultipart("questionFormat")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						
						CardInfoField field = cardInfoFields.get(keyName);
						field.keyName = keyName;
						field.questionFormat = questionFormat;
						
						field.handleAdd();
						cardInfoFields.put(field.keyName, field);
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/infofield\">/admin/infofield</a>",
							"Location: /admin/infofield"
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/settings"))
				{
					String resp = "<body>";
					resp += "<form enctype=\"multipart/form-data\" action=\"/admin/settings\" method=\"post\">";
					resp += "<h1>Settings</h1>";
					resp += "<h2>Drops</h2>";
					resp += "Number of cards to drop at once: <input name=\"dropNumCards\" value=\"" + settings.dropNumCards + "\"/><br>";
					resp += "Drop cooldown (milliseconds): <input name=\"dropCooldownMillis\" value=\"" + settings.dropCooldownMillis + "\"/><br>";
					resp += "<h2>Dungeon</h2>";
					resp += "Number of dungeon answers to choose from: <input name=\"dungeonOptions\" value=\"" + settings.dungeonOptions + "\"/><br>";
					resp += "Dungeon cooldown (milliseconds): <input name=\"dungeonCooldownMillis\" value=\"" + settings.dungeonCooldownMillis + "\"/><br>";
					resp += "<h2>Bot Config</h2>";
					resp += "Bot prefix: <input name=\"botPrefix\" value=\"" + settings.botPrefix + "\"/><br>";
					resp += "Bot client ID: <input name=\"botClientId\" value=\"" + settings.botClientId + "\"/><br>";
					resp += "Bot token (RESTART REQUIRED): <input name=\"botToken\" value=\"" + settings.botToken + "\"/><br>";
					resp += "<h2>App Config (RESTART REQUIRED for all)</h2>";
					resp += "Local Port: <input name=\"serverPort\" value=\"" + settings.serverPort + "\"/><br>";
					resp += "Public URL: <input name=\"siteUrl\" value=\"" + settings.siteUrl + "\"/><br>";
					resp += "Auth Handler Public URL: <input name=\"authHandler\" value=\"" + settings.authHandler + "\"/><br>";
					resp += "Card Images Folder: <input name=\"cardsFolder\" value=\"" + settings.cardsFolder + "\"/><br>";
					resp += "<input type=\"submit\" value=\"Save changes\"/>";
					resp += "</form>";
					resp += "<a href=\"/\">Cancel</a>";
					resp += "</body>";
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/settings"))
				{
					try
					{
						int dropNumCardsCandidate = Integer.parseInt(new String(req.getMultipart("dropNumCards")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						int dropCooldownMillisCandidate = Integer.parseInt(new String(req.getMultipart("dropCooldownMillis")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						int dungeonOptionsCandidate = Integer.parseInt(new String(req.getMultipart("dungeonOptions")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						int dungeonCooldownMillisCandidate = Integer.parseInt(new String(req.getMultipart("dungeonCooldownMillis")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						String botPrefixCandidate = new String(req.getMultipart("botPrefix")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String botClientIdCandidate = new String(req.getMultipart("botClientId")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String botTokenCandidate = new String(req.getMultipart("botToken")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						int serverPortCandidate = Integer.parseInt(new String(req.getMultipart("serverPort")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						String siteUrlCandidate = new String(req.getMultipart("siteUrl")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String authHandlerCandidate = new String(req.getMultipart("authHandler")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String cardsFolderCandidate = new String(req.getMultipart("cardsFolder")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						
						Settings newSettings = new Settings();
						newSettings.dropNumCards = dropNumCardsCandidate;
						newSettings.dropCooldownMillis = dropCooldownMillisCandidate;
						newSettings.dungeonOptions = dungeonOptionsCandidate;
						newSettings.dungeonCooldownMillis = dungeonCooldownMillisCandidate;
						newSettings.botPrefix = botPrefixCandidate;
						newSettings.botClientId = botClientIdCandidate;
						newSettings.botToken = botTokenCandidate;
						newSettings.serverPort = serverPortCandidate;
						newSettings.siteUrl = siteUrlCandidate;
						newSettings.authHandler = authHandlerCandidate;
						newSettings.cardsFolder = cardsFolderCandidate;
						
						newSettings.handleUpdate(settings);
						
						settings = newSettings;
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/settings\">/admin/settings</a>",
							"Location: /admin/settings"
						);
					}
					catch (SQLException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else
				{
					req.respond(HttpStatus.NOT_FOUND_404);
				}
			});
			System.out.println("The web server should be up and running!");
		}
		catch (Exception ex)
		{
			System.out.println("Uh-oh! Unhandled exception in the web server code.");
			ex.printStackTrace();
			System.out.println("Note: The Discord bot will not be started; if it has already started it will be stopped.");
			System.exit(0);
		}
		
		try
		{
			final DiscordClient client = DiscordClient.create(settings.botToken);
			final GatewayDiscordClient gateway = client.login().block();
			
			gateway.on(MessageCreateEvent.class).subscribe(event -> {
				try
				{
					final Message message = event.getMessage();
					final String command = message.getContent().split(" ")[0].trim();
					final GuildMessageChannel discordChannelObj = (GuildMessageChannel)message.getChannel().block();
					final var discordUserObj = message.getAuthor().orElseThrow();
					final var discordGuildObj = discordChannelObj.getGuild().block().getId();
					final var discordMemberObj = discordUserObj.asMember(discordGuildObj).block();
					final String discordUserId = discordUserObj.getId().asString();
					final String nickname = discordMemberObj.getDisplayName();
					User user = users.get(discordUserId);
					if (user == null)
					{
						try
						{
							user = new User();
							user.userId = discordUserId;
							user.handleAdd();
							users.put(user.userId, user);
						}
						catch (Exception ex)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I ran into an internal error trying to create your user!\n" + ex.getMessage()).subscribe();
							return;
						}
					}
					if ("~help".replaceAll("\\~", settings.botPrefix).equalsIgnoreCase(command))
					{
						discordChannelObj.createMessage("Commands:\n  ~drop\n  ~inv\n  ~view [id]\n  ~dg\n  ~cd\n  ~help".replaceAll("\\~", settings.botPrefix)).block();
					}
					if ("~drop".replaceAll("\\~", settings.botPrefix).equalsIgnoreCase(command))
					{
						if (System.currentTimeMillis()-user.lastDropTime < settings.dropCooldownMillis)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to wait another " + formatDuration(settings.dropCooldownMillis-(System.currentTimeMillis()-user.lastDropTime)) + " to drop :(").subscribe();
							return;
						}
						user.lastDropTime = System.currentTimeMillis();
						
						int numCardsForDrop = settings.dropNumCards;
						
						CardDef[] cards = cardDefinitions.values().toArray(CardDef[]::new);
						if (cards.length == 0)
						{
							discordChannelObj.createMessage("Sorry " + nickname + ", I don't have any cards to drop yet!\nAsk an admin to add some using the admin panel at https://" + settings.siteUrl + "/admin/cards");
						}
						
						String cardStrSend = "";
						ArrayList<CardDef> cardSend = new ArrayList<CardDef>();
						for (int i = 0; i < settings.dropNumCards; i++)
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
							BufferedImage combined = stitchImages(cardSend);
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							ImageIO.write(combined, "webp", baos);
							
							RenderedImageStorage cachedImage = new RenderedImageStorage();
							cachedImage.cachedData = baos.toByteArray();
							renderedImageCache.put(cardStrSendFinal, cachedImage);
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
							temp.setImage("https://" + settings.siteUrl + "/cardpack?" + cardStrSendFinal)
							.setFooter("drops?", "https://" + settings.siteUrl + "/img/botprofile.png")
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
					if ("~inv".replaceAll("\\~", settings.botPrefix).equalsIgnoreCase(command))
					{
						String cardsStr = "";
						for (CardInst card : user.inventory.values())
							cardsStr += " - " + card.def.displayName + " [" + card.id + "] (" + card.stars + " stars, level " + card.level + ")\n";
						discordChannelObj.createMessage(nickname + "'s Inventory:\n" + cardsStr).block();
					}
					if ("~view".replaceAll("\\~", settings.botPrefix).equalsIgnoreCase(command))
					{
						String id = message.getContent().split(" ")[1].trim();
						CardInst card = cardInstances.get(id);
						if (card != null)
						{
							discordChannelObj.createEmbed(spec ->
								spec.setTitle(card.def.displayName)
								.setDescription(card.def.displayDescription)
								.addField("ID", card.id, true)
								.addField("Stars", card.stars+"", true)
								.addField("Level", card.level+"", true)
								.setImage("https://" + settings.siteUrl + "/card/" + card.def.imageFilename)
								.setFooter("drops?", "https://" + settings.siteUrl + "/img/botprofile.png")
								.setTimestamp(Instant.now())
							).subscribe();
						}
						else
						{
							discordChannelObj.createMessage("Sorry " + nickname +", I couldn't find card \"" + id + "\" :(").subscribe();
						}
					}
					if ("~dg".replaceAll("\\~", settings.botPrefix).equalsIgnoreCase(command))
					{
						if (System.currentTimeMillis()-user.lastDungeonTime < settings.dungeonCooldownMillis)
						{
							discordChannelObj.createMessage("Sorry " + nickname +", you need to wait another " + formatDuration(settings.dungeonCooldownMillis-(System.currentTimeMillis()-user.lastDungeonTime)) + " to attempt a dungeon :(").subscribe();
							return;
						}
						user.lastDungeonTime = System.currentTimeMillis();
						
						// If the settings value changes in the middle of this, we don't want it to break things
						final int numOptionsForDungeon = settings.dungeonOptions;
						
						HashMap<CardInfoField, ArrayList<String>> dungeonFieldOptions = new HashMap<CardInfoField, ArrayList<String>>();
						for (CardInfoField field : cardInfoFields.values())
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
							for (CardDef card : cardDefinitions.values())
								if (card.info.get(dungeonField.keyName) != null && card.info.get(dungeonField.keyName).size() > 0)
									cardOptions.add(card);
							CardDef dungeonCard = cardOptions.get((int)(Math.random()*cardOptions.size()));
							
							ArrayList<CardInfoFieldEntry> possibleCorrectEntries = dungeonCard.info.get(dungeonField.keyName);
							CardInfoFieldEntry correctEntry = possibleCorrectEntries.get((int)(Math.random()*possibleCorrectEntries.size()));
							
							String[] dungeonValues = new String[numOptionsForDungeon];
							int correctEntryIndex = -1;
							Collections.shuffle(dungeonFieldUniqueValues);
							for (int i = 0; i < dungeonValues.length; i++)
							{
								dungeonValues[i] = dungeonFieldUniqueValues.get((int)(Math.random()*dungeonFieldUniqueValues.size()));
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
							
							discordChannelObj.createEmbed(spec -> {
								var temp = spec.setColor(Color.RED)
								.setThumbnail(message.getAuthor().orElseThrow(null).getAvatarUrl())
								.setTitle("Dungeon for " + nickname + ":")
								.setDescription(dungeonCard.displayName + " - " + dungeonField.questionFormat);
								for (int i = 0; i < dungeonValues.length; i++)
								{
									temp = temp.addField(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}), dungeonValues[i], true);
								}
								temp.setImage("https://" + settings.siteUrl + "/card/" + dungeonCard.imageFilename)
								.setFooter("drops?", "https://" + settings.siteUrl + "/img/botprofile.png")
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
							discordChannelObj.createMessage("Sorry " + nickname + ", I don't have enough card info to create dungeons with the specified option number of " + settings.dungeonOptions + ".\nAsk an admin to add more card info using the admin panel at https://" + settings.siteUrl + "/admin/cards or lower the dungeon option number.");
						}
						
					}
					if ("~cd".replaceAll("\\~", settings.botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
					{
						long dropCd = settings.dropCooldownMillis - (System.currentTimeMillis() - user.lastDropTime);
						long dungeonCd = settings.dungeonCooldownMillis - (System.currentTimeMillis() - user.lastDungeonTime);
						discordChannelObj.createEmbed(spec ->
							spec.setTitle("Cooldowns")
								.setDescription("for " + nickname)
								.setThumbnail(discordUserObj.getAvatarUrl())
								.addField("Drop", (dropCd <= 0 ? "[READY]" : formatDuration(dropCd)), false)
								.addField("Dungeon", (dungeonCd <= 0 ? "[READY]" : formatDuration(dungeonCd)), true)
								.setFooter("drops?", "https://" + settings.siteUrl + "/img/botprofile.png")
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
						User user = users.get(discordUserId);
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
									card.handleAdd();
									cardInstances.put(card.id, card);
									dropInfo.channel.createMessage("Enjoy your new " + card.stars + " star " + card.def.displayName + " (level " + card.level + ")").subscribe();
									pendingDropInfo.remove(dropInfo);
								}
								catch (SQLException ex)
								{
									dropInfo.channel.createMessage("Sorry " + dropInfo.nickname + ", the server encountered an error while processing your request.\n" + ex.getMessage()).subscribe();
								}
								renderedImageCache.remove(dropInfo.cacheKey);
							}
						}
						else if (dungeonInfo != null && dungeonInfo.messageId.equals(discordMessageId))
						{
							String raw = event.getEmoji().asUnicodeEmoji().orElseThrow().getRaw();
							byte[] b = raw.getBytes();
							if (b.length == 7 && b[1] == (byte)-17 && b[2] == (byte)-72 && b[3] == (byte)-113 && b[4] == (byte)-30 && b[5] == (byte)-125 && b[6] == (byte)-93)
							{
								pendingDungeonInfo.remove(dungeonInfo);
								int ind = raw.charAt(0)-0x31+1;
								if (ind == dungeonInfo.correctEntryIndex)
								{
									try
									{
										CardInst card = genCard(dungeonInfo.card);
										card.handleAdd();
										cardInstances.put(card.id, card);
										dropInfo.channel.createMessage("Enjoy your new " + card.stars + " star " + card.def.displayName + " (level " + card.level + ")").subscribe();
									}
									catch (SQLException ex)
									{
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
			System.out.println("Uh-oh! Unhandled exception in the Discord bot code.\n\tbotToken: " + settings.botToken);
			ex.printStackTrace();
			System.out.println("Note: The web-server will continue running.");
		}
		
	}
	public static <T> ArrayList<T> list(T... items)
	{
		return new ArrayList<T>(Arrays.asList(items));
	}
	public static BufferedImage stitchImages(List<CardDef> packCards) throws IOException
	{
		return stitchImages(packCards.stream().map(def -> def.imageFilename).toArray(String[]::new));
	}
	public static BufferedImage stitchImages(String[] packCards) throws IOException
	{
		ArrayList<BufferedImage> packImages = new ArrayList<BufferedImage>();
		int mh = 0, tw = 0;
		for (String card : packCards)
		{
			System.out.println(card);
			if ((card.indexOf("/") != -1) || (card.indexOf("..") != -1))
				throw new RuntimeException("Haha yeah, no.");
			BufferedImage img = ImageIO.read(Paths.get(settings.cardsFolder, card).toFile());
			mh = Math.max(mh,img.getHeight());
			tw += img.getWidth();
			packImages.add(img);
		}
		System.out.println("Gen combined");
		double scaleFactor = 1.0;
		if (tw > 1600)
		{
			scaleFactor = 1600.0/tw;
			tw = 1600;
			mh = (int)(mh*scaleFactor);
		}
		BufferedImage combined = new BufferedImage(tw,mh,3);
		int x = 0;
		for (BufferedImage image : packImages)
		{
			combined.getGraphics().drawImage(image, (int)(x*scaleFactor), (int)((mh/scaleFactor-image.getHeight())/2*scaleFactor), (int)(scaleFactor*image.getWidth()), (int)(scaleFactor*image.getHeight()), null);
			x += image.getWidth();
		}
		System.out.println(" . . .");
		return combined;
	}
	
	public static CardInst genCard(CardDef cardDef)
	{
		String id = null;
		byte[] idbytes = new byte[4];
		do
		{
			rand.nextBytes(idbytes);
			id = SysUtils.encodeBase64(idbytes).replaceAll("[^a-zA-Z0-9]","").toLowerCase();
		}
		while (cardInstances.get(id) != null);
		CardInst ret = new CardInst();
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

abstract class DBEnabledClass
{
	static Connection connection;
	protected DBEnabledClass clone()
	{
		try
		{
			return (DBEnabledClass)super.clone();
		}
		catch (Exception ex)
		{
			throw new RuntimeException("DBEnabledClass clone failed!");
		}
	}
	void handleAdd() throws SQLException
	{
		addToDatabase();
		addToObjects();
	}
	abstract void addToDatabase() throws SQLException;
	abstract void addToObjects();
	void handleUpdate(DBEnabledClass previous) throws SQLException
	{
		updateInDatabase();
		updateInObjects(previous);
	}
	abstract void updateInDatabase() throws SQLException;
	void updateInObjects(DBEnabledClass previous)
	{
		if (previous != null)
		{
			previous.removeFromObjects();
			addToObjects();
		}
		else
		{
			throw new RuntimeException("Previous version of the object was not provided to updateInObjects()!");
		}
	}
	void handleRemove() throws SQLException
	{
		removeFromDatabase();
		removeFromObjects();
	}
	abstract void removeFromDatabase() throws SQLException;
	abstract void removeFromObjects();
}

class DatabaseManager
{
	public static void connectToDatabase() throws SQLException
	{
		ExampleBot.connection = DriverManager.getConnection("jdbc:sqlite:" + ExampleBot.DATABASE_LOCATION);
	}
	public static void initAllTables() throws SQLException
	{
		DBEnabledClass.connection = ExampleBot.connection;
		User.tableInit();
		CardPack.tableInit();
		CardDef.tableInit();
		CardInst.tableInit();
		CardInfoField.tableInit();
		CardInfoFieldEntry.tableInit();
		Settings.tableInit();
	}
	public static void readAllFromDatabase() throws SQLException
	{
		User.readAllFromDatabaseInit(ExampleBot.users);
		CardPack.readAllFromDatabaseInit(ExampleBot.cardPacks);
		CardDef.readAllFromDatabaseInit(ExampleBot.cardDefinitions);
		CardInst.readAllFromDatabaseInit(ExampleBot.cardInstances);
		CardInfoField.readAllFromDatabaseInit(ExampleBot.cardInfoFields);
		CardInfoFieldEntry.readAllFromDatabaseInit(ExampleBot.cardInfoFieldEntries);
		
		User.readAllFromDatabaseFinalize(ExampleBot.users, ExampleBot.cardPacks, ExampleBot.cardDefinitions, ExampleBot.cardInstances, ExampleBot.cardInfoFields, ExampleBot.cardInfoFieldEntries);
		CardPack.readAllFromDatabaseFinalize(ExampleBot.users, ExampleBot.cardPacks, ExampleBot.cardDefinitions, ExampleBot.cardInstances, ExampleBot.cardInfoFields, ExampleBot.cardInfoFieldEntries);
		CardDef.readAllFromDatabaseFinalize(ExampleBot.users, ExampleBot.cardPacks, ExampleBot.cardDefinitions, ExampleBot.cardInstances, ExampleBot.cardInfoFields, ExampleBot.cardInfoFieldEntries);
		CardInst.readAllFromDatabaseFinalize(ExampleBot.users, ExampleBot.cardPacks, ExampleBot.cardDefinitions, ExampleBot.cardInstances, ExampleBot.cardInfoFields, ExampleBot.cardInfoFieldEntries);
		CardInfoField.readAllFromDatabaseFinalize(ExampleBot.users, ExampleBot.cardPacks, ExampleBot.cardDefinitions, ExampleBot.cardInstances, ExampleBot.cardInfoFields, ExampleBot.cardInfoFieldEntries);
		CardInfoFieldEntry.readAllFromDatabaseFinalize(ExampleBot.users, ExampleBot.cardPacks, ExampleBot.cardDefinitions, ExampleBot.cardInstances, ExampleBot.cardInfoFields, ExampleBot.cardInfoFieldEntries);
		
		ExampleBot.settings = Settings.readFromDatabase();
	}
}

class User extends DBEnabledClass
{
	String userId;
	long lastDropTime;
	long lastDungeonTime;
	
	Map<String,CardInst> inventory = new HashMap<String,CardInst>();
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS user ("
				+ "userid string UNIQUE,"
				+ "lastDropTime long NOT NULL,"
				+ "lastDungeonTime long NOT NULL,"
				+ "PRIMARY KEY (userid)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(Map<String,User> storage) throws SQLException
	{
		ResultSet userRS = connection.createStatement().executeQuery("SELECT * FROM user");
		while (userRS.next())
		{
			User obj = new User();
			obj.userId = userRS.getString("userid");
			obj.lastDropTime = userRS.getLong("lastDropTime");
			obj.lastDungeonTime = userRS.getLong("lastDungeonTime");
			storage.put(obj.userId, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(Map<String,User> users, Map<String,CardPack> cardPacks, Map<String,CardDef> cardDefs, Map<String,CardInst> cardInsts, Map<String,CardInfoField> cardInfoFields, Map<String,CardInfoFieldEntry> cardInfoFieldEntries) throws SQLException
	{
		return;
	}
	
	void addToDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO user ("
				+ "userid, lastDropTime, lastDungeonTime"
			+ ") VALUES ("
				+ "'" + userId + "', " + lastDropTime + ", " + lastDungeonTime
			+ ")"
		);
	}
	void addToObjects() {}
	void updateInDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"UPDATE user SET "
			+ "lastDropTime = " + lastDropTime + ", "
			+ "lastDungeonTime = " + lastDungeonTime
			+ " WHERE userid = '" + userId + "'"
		);
	}
	void removeFromDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"DELETE FROM user"
			+ " WHERE userid = '" + userId + "'"
		);
	}
	void removeFromObjects() {}
}

class CardPack extends DBEnabledClass
{
	String packName;
	
	Map<String,CardDef> cards = new HashMap<String,CardDef>();
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardPack ("
				+ "packName string"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(Map<String,CardPack> storage) throws SQLException
	{
		ResultSet cardPackRS = connection.createStatement().executeQuery("SELECT * FROM cardPack");
		while (cardPackRS.next())
		{
			CardPack obj = new CardPack();
			obj.packName = cardPackRS.getString("packName");
			storage.put(obj.packName, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(Map<String,User> users, Map<String,CardPack> cardPacks, Map<String,CardDef> cardDefs, Map<String,CardInst> cardInsts, Map<String,CardInfoField> cardInfoFields, Map<String,CardInfoFieldEntry> cardInfoFieldEntries) throws SQLException
	{
		return;
	}
	
	void addToDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO cardPack ("
				+ "packName"
			+ ") VALUES ("
				+ "'" + packName + "'"
			+ ")"
		);
	}
	void addToObjects() {}
	void updateInDatabase() throws SQLException
	{
		throw new RuntimeException("Card Packs currently cannot be updated.");
	}
	void removeFromDatabase() throws SQLException
	{
		throw new RuntimeException("Card Packs currently cannot be deleted.");
	}
	void removeFromObjects() {}
}

class CardDef extends DBEnabledClass
{
	String imageFilename;
	String displayName;
	String displayDescription;
	CardPack cardPack;
	
	Map<String,CardInst> instances = new HashMap<String,CardInst>();
	Map<CardInfoField,ArrayList<CardInfoFieldEntry>> info = new HashMap<CardInfoField,ArrayList<CardInfoFieldEntry>>();
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardDefinition ("
				+ "imageFilename string UNIQUE,"
				+ "displayName string,"
				+ "displayDescription string,"
				+ "packName string NOT NULL,"
				+ "FOREIGN KEY (packName)"
					+ " REFERENCES cardPack(packName),"
				+ "PRIMARY KEY (imageFilename)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(Map<String,CardDef> storage) throws SQLException
	{
		ResultSet cardDefinitionRS = connection.createStatement().executeQuery("SELECT * FROM cardDefinition");
		while (cardDefinitionRS.next())
		{
			CardDef obj = new CardDef();
			obj.imageFilename = cardDefinitionRS.getString("imageFilename");
			obj.displayName = cardDefinitionRS.getString("displayName");
			obj.displayDescription = cardDefinitionRS.getString("displayDescription");
			storage.put(obj.imageFilename, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(Map<String,User> users, Map<String,CardPack> cardPacks, Map<String,CardDef> cardDefs, Map<String,CardInst> cardInsts, Map<String,CardInfoField> cardInfoFields, Map<String,CardInfoFieldEntry> cardInfoFieldEntries) throws SQLException
	{
		ResultSet cardDefinitionRS = connection.createStatement().executeQuery("SELECT * FROM cardDefinition");
		while (cardDefinitionRS.next())
		{
			CardDef obj = cardDefs.get(cardDefinitionRS.getString("imageFilename"));
			
			obj.cardPack = cardPacks.get(cardDefinitionRS.getString("packName"));
			
			obj.addToObjects();
		}
	}
	
	void addToDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO cardDefinition ("
				+ "imageFilename, displayName, displayDescription, packName"
			+ ") VALUES ("
				+ "'" + imageFilename + "', '" + displayName + "', '" + displayDescription + "', '" + cardPack.packName + "'"
			+ ")"
		);
	}
	void addToObjects()
	{
		cardPack.cards.put(imageFilename, this);
	}
	void updateInDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"UPDATE cardDefinition SET "
				+ "displayName = '" + displayName + "',"
				+ "displayDescription = '" + displayDescription + "',"
				+ "packName = '" + cardPack.packName + "'"
			+ " WHERE imageFilename = '" + imageFilename + "'"
		);
	}
	void removeFromDatabase() throws SQLException
	{
		throw new RuntimeException("Card Definitions currently cannot be deleted.");
	}
	void removeFromObjects()
	{
		cardPack.cards.remove(imageFilename);
	}
}

class CardInst extends DBEnabledClass
{
	CardDef def;
	String id;
	int level;
	int stars;
	User owner;
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardInstance ("
				+ "rawName string NOT NULL,"
				+ "id string UNIQUE,"
				+ "level integer NOT NULL,"
				+ "stars integer NOT NULL,"
				+ "owner string,"
				+ "FOREIGN KEY (rawName)"
					+ " REFERENCES cardDefinition(imageFilename),"
				+ "FOREIGN KEY (owner)"
					+ " REFERENCES user(userid),"
				+ "PRIMARY KEY (id)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(Map<String,CardInst> storage) throws SQLException
	{
		ResultSet cardInstanceRS = connection.createStatement().executeQuery("SELECT * FROM cardInstance");
		while (cardInstanceRS.next())
		{
			CardInst obj = new CardInst();
			obj.id = cardInstanceRS.getString("id");
			obj.level = cardInstanceRS.getInt("level");
			obj.stars = cardInstanceRS.getInt("stars");
			storage.put(obj.id, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(Map<String,User> users, Map<String,CardPack> cardPacks, Map<String,CardDef> cardDefs, Map<String,CardInst> cardInsts, Map<String,CardInfoField> cardInfoFields, Map<String,CardInfoFieldEntry> cardInfoFieldEntries) throws SQLException
	{
		ResultSet cardInstanceRS = connection.createStatement().executeQuery("SELECT * FROM cardInstance");
		while (cardInstanceRS.next())
		{
			CardInst obj = cardInsts.get(cardInstanceRS.getString("id"));
				
			obj.def = cardDefs.get(cardInstanceRS.getString("rawName"));
			obj.owner = users.get(cardInstanceRS.getString("owner"));
			
			obj.addToObjects();
		}
	}
	
	void addToDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO cardInstance ("
				+ "rawName, id, level, stars, owner"
			+ ") VALUES ("
				+ "'" + def.imageFilename + "', '" + id + "', '" + level + "', '" + stars + "', '" + owner.userId + "'"
			+ ")"
		);
	}
	void addToObjects()
	{
		def.instances.put(id, this);
		owner.inventory.put(id, this);
	}
	void updateInDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"UPDATE cardInstance SET "
				+ "rawName = '" + def.imageFilename + "',"
				+ "level = '" + level + "',"
				+ "stars = '" + stars + "'"
				+ "owner = '" + owner.userId + "'"
			+ " WHERE id = '" + id + "'"
		);
	}
	void removeFromDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"DELETE FROM cardInstance"
			+ " WHERE id = '" + id + "'"
		);
	}
	void removeFromObjects()
	{
		def.instances.remove(id);
		owner.inventory.remove(id);
	}
}

class CardInfoField extends DBEnabledClass
{
	String keyName;
	String questionFormat;
	
	Map<String,CardInfoFieldEntry> entries = new HashMap<String,CardInfoFieldEntry>();
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardInfoField ("
				+ "keyName string UNIQUE,"
				+ "questionFormat string NOT NULL,"
				+ "PRIMARY KEY (keyName)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(Map<String,CardInfoField> storage) throws SQLException
	{
		ResultSet cardInfoRS = connection.createStatement().executeQuery("SELECT * FROM cardInfoField");
		while (cardInfoRS.next())
		{
			CardInfoField obj = new CardInfoField();
			obj.keyName = cardInfoRS.getString("keyName");
			obj.questionFormat = cardInfoRS.getString("questionFormat");
			storage.put(obj.keyName, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(Map<String,User> users, Map<String,CardPack> cardPacks, Map<String,CardDef> cardDefs, Map<String,CardInst> cardInsts, Map<String,CardInfoField> cardInfoFields, Map<String,CardInfoFieldEntry> cardInfoFieldEntries) throws SQLException
	{
		return;
	}
	
	void addToDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO cardInfoField ("
				+ "keyName, questionFormat"
			+ ") VALUES ("
				+ "'" + keyName + "', '" + questionFormat + "'"
			+ ")"
		);
	}
	void addToObjects() {}
	void updateInDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"UPDATE cardInfoField SET "
				+ "questionFormat = '" + questionFormat + "'"
			+ " WHERE keyName = '" + keyName + "'"
		);
	}
	void removeFromDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"DELETE FROM cardInstance"
			+ " WHERE keyName = '" + keyName + "'"
		);
	}
	void removeFromObjects() {}
}

class CardInfoFieldEntry extends DBEnabledClass
{
	String id;
	CardDef card;
	CardInfoField field;
	String value;
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardInfoEntry ("
				+ "id string PRIMARY KEY,"
				+ "card string NOT NULL,"
				+ "field string NOT NULL,"
				+ "value string NOT NULL,"
				+ "FOREIGN KEY (card)"
					+ " REFERENCES cardDefinition(imageFilename),"
				+ "FOREIGN KEY (field)"
					+ " REFERENCES cardInfoField(keyName)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(Map<String,CardInfoFieldEntry> storage) throws SQLException
	{
		ResultSet cardInfoERS = connection.createStatement().executeQuery("SELECT * FROM cardInfoEntry");
		while (cardInfoERS.next())
		{
			CardInfoFieldEntry obj = new CardInfoFieldEntry();
			obj.id = cardInfoERS.getString("id");
			obj.value = cardInfoERS.getString("value");
			storage.put(obj.id, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(Map<String,User> users, Map<String,CardPack> cardPacks, Map<String,CardDef> cardDefs, Map<String,CardInst> cardInsts, Map<String,CardInfoField> cardInfoFields, Map<String,CardInfoFieldEntry> cardInfoFieldEntries) throws SQLException
	{
		ResultSet cardInfoERS = connection.createStatement().executeQuery("SELECT * FROM cardInfoEntry");
		while (cardInfoERS.next())
		{
			CardInfoFieldEntry obj = cardInfoFieldEntries.get(cardInfoERS.getString("id"));
				
			obj.card = cardDefs.get(cardInfoERS.getString("card"));
			obj.field = cardInfoFields.get(cardInfoERS.getString("field"));
			
			obj.addToObjects();
		}
	}
	
	void addToDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"INSERT INTO cardInfoEntry ("
				+ "id, card, field, value"
			+ ") VALUES ("
				+ "'" + id + "', '" + card.imageFilename + "', '" + field.keyName + "', '" + value + "'"
			+ ")"
		);
	}
	void addToObjects()
	{
		field.entries.put(id, this);
		if (card.info.get(field) == null)
			card.info.put(field, new ArrayList<CardInfoFieldEntry>());
		card.info.get(field).add(this);
	}
	void updateInDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"UPDATE cardInfoEntry SET "
				+ "card = '" + card.imageFilename + "',"
				+ "field = '" + field.keyName + "',"
				+ "value = '" + value + "'"
			+ " WHERE id = '" + id + "'"
		);
	}
	// Needs custom logic because the objects are stored directly in an array in CardDef.info, not a hashmap with an index as key
	void updateInObjects(DBEnabledClass previousGeneric)
	{
		CardInfoFieldEntry previous = (CardInfoFieldEntry)previousGeneric;
		field.entries.remove(previous.id);
		for (CardInfoFieldEntry entry : card.info.get(field))
		{
			if (entry.id == previous.id)
			{
				card.info.get(field).remove(entry);
				break;
			}
		}
	}
	void removeFromDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"DELETE FROM cardInfoEntry"
			+ " WHERE id = '" + id + "'"
		);
	}
	void removeFromObjects()
	{
		field.entries.remove(id);
		card.info.get(field).remove(this);
	}
}

class Settings extends DBEnabledClass
{
	
	// Number of cards to drop at once
	int dropNumCards;
	
	// Cooldown before a user is allowed to drop again
	int dropCooldownMillis;
	
	// Number of answer choices to be presented to the user during a dungeon run (only 1 will be correct)
	int dungeonOptions;
	
	// Cooldown before a user is allowed to attempt another dungeon
	int dungeonCooldownMillis;
	
	// Local port to run the webserver on
	int serverPort;
	
	// Prefix to trigger the bot on Discord
	String botPrefix;
	
	// Actual public-facing URL
	String siteUrl;
	
	// Absolute path to card image folder
	String cardsFolder;
	
	// URL of the auth handler (authenticates admins to the web server)
	String authHandler;
	
	// Token of the Discord bot (this should be kept secret!)
	String botToken;
	
	// Client ID of the Discord bot's application (this can be made public!)
	String botClientId;
	
	static void tableInit() throws SQLException
	{
		connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS settings ("
				+ "dropNumCards int NOT NULL,"
				+ "dropCooldownMillis int NOT NULL,"
				+ "dungeonOptions int NOT NULL,"
				+ "dungeonCooldownMillis int NOT NULL,"
				+ "serverPort int NOT NULL,"
				+ "botPrefix string NOT NULL,"
				+ "siteUrl string NOT NULL,"
				+ "cardsFolder string NOT NULL,"
				+ "authHandler string NOT NULL,"
				+ "botToken string NOT NULL,"
				+ "botClientId string NOT NULL"
			+ ")"
		);
	}
	
	static Settings readFromDatabase() throws SQLException
	{
		ResultSet settingsRS = connection.createStatement().executeQuery(
			"SELECT * FROM settings"
			+ " LIMIT 1"
		);
		if (!settingsRS.next())
		{
			connection.createStatement().executeUpdate(
				"INSERT INTO settings ("
					+ "dropNumCards, dropCooldownMillis, dungeonOptions, dungeonCooldownMillis, serverPort, botPrefix, siteUrl, cardsFolder, authHandler, botToken, botClientId"
				+ ") VALUES ("
					+ "3, 600000, 4, 600000, 28002, ',', 'drops.0k.rip', '/www/drops.0k.rip/card/', 'auth.aws1.0k.rip', 'INVALID_TOKEN_REPLACE_ME', 'INVALID_CLIENT_ID_REPLACE_ME'"
				+ ")"
			);
			return readFromDatabase();
		}
		Settings settings = new Settings();
		settings.dropNumCards = settingsRS.getInt("dropNumCards");
		settings.dropCooldownMillis = settingsRS.getInt("dropCooldownMillis");
		settings.dungeonOptions = settingsRS.getInt("dungeonOptions");
		settings.dungeonCooldownMillis = settingsRS.getInt("dungeonCooldownMillis");
		settings.serverPort = settingsRS.getInt("serverPort");
		settings.botPrefix = settingsRS.getString("botPrefix");
		settings.siteUrl = settingsRS.getString("siteUrl");
		settings.cardsFolder = settingsRS.getString("cardsFolder");
		settings.authHandler = settingsRS.getString("authHandler");
		settings.botToken = settingsRS.getString("botToken");
		settings.botClientId = settingsRS.getString("botClientId");
		return settings;
	}
	
	void addToDatabase() throws SQLException
	{
		throw new RuntimeException("Settings currently cannot be added.");
	}
	void addToObjects() {}
	void updateInDatabase() throws SQLException
	{
		connection.createStatement().executeUpdate(
			"UPDATE settings SET "
				+ "dropNumCards = " + dropNumCards + ","
				+ "dropCooldownMillis = " + dropCooldownMillis + ","
				+ "dungeonOptions = " + dungeonOptions + ","
				+ "dungeonCooldownMillis = " + dungeonCooldownMillis + ","
				+ "serverPort = " + serverPort + ","
				+ "botPrefix = '" + botPrefix + "',"
				+ "siteUrl = '" + siteUrl + "',"
				+ "cardsFolder = '" + cardsFolder + "',"
				+ "authHandler = '" + authHandler + "',"
				+ "botToken = '" + botToken + "',"
				+ "botClientId = '" + botClientId + "'"
		);
	}
	void removeFromDatabase() throws SQLException
	{
		throw new RuntimeException("Settings currently cannot be deleted.");
	}
	void removeFromObjects() {}
}


class PendingDropInfo
{
	User user;
	String nickname;
	MessageChannel channel;
	String messageId;
	ArrayList<CardDef> cards;
	String cacheKey;
}

class PendingDungeonInfo
{
	User user;
	String nickname;
	MessageChannel channel;
	String messageId;
	int correctEntryIndex;
	CardDef card;
}

class RenderedImageStorage
{
	User user;
	byte[] cachedData;
}

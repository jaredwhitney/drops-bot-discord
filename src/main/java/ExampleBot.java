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
	public static Map<String,HashMap<String,ArrayList<String>>> cardInfo = new HashMap<String,HashMap<String,ArrayList<String>>>();
	public static Map<String,ArrayList<String>> cardPacks = new HashMap<String,ArrayList<String>>();
	public static Map<String,ArrayList<String>> inventory = new HashMap<String,ArrayList<String>>();
	public static Map<String,Boolean> idLookup = new HashMap<String,Boolean>();
	public static Map<String,Long> dropTime = new HashMap<String,Long>();
	public static Map<String,Long> dgTime = new HashMap<String,Long>();
	public static Map<String,ArrayList<String>> dgopts = new HashMap<String,ArrayList<String>>();
	public static final HashMap<String,String> dgCatMap = new HashMap<String,String>();
	
	// need no population from SQL database
	public static Map<String,MessageChannel> dropChannel = new HashMap<String,MessageChannel>();
	public static Map<String,String[]> dgWaiting = new HashMap<String,String[]>();
	public static Map<String,String[]> dropWaiting = new HashMap<String,String[]>();
	public static Map<String,byte[]> renderedImage = new HashMap<String,byte[]>();
	public static Map<String,String> riOwner = new HashMap<String,String>();
	public static final Random rand = new Random();
	
	public static int dropNumCards, dropCooldownMillis, dungeonNumOptions, dungeonCooldownMillis;
	public static String botPrefix, botClientId;
	
	public static void main(final String[] args) throws SQLException
	{
		
		final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_LOCATION);
		final Statement statement = connection.createStatement();
		statement.execute("CREATE TABLE IF NOT EXISTS user (userid string UNIQUE, lastDropTime long NOT NULL, lastDungeonTime long NOT NULL, PRIMARY KEY (userid))");
		statement.execute("CREATE TABLE IF NOT EXISTS cardPack (packName string)");
		statement.execute("CREATE TABLE IF NOT EXISTS cardDefinition (imageFilename string UNIQUE, displayName string, displayDescription string, packName string NOT NULL, FOREIGN KEY (packName) REFERENCES cardPack(packName), PRIMARY KEY (imageFilename))");
		statement.execute("CREATE TABLE IF NOT EXISTS cardInstance (rawName string NOT NULL, id string UNIQUE, level integer NOT NULL, stars integer NOT NULL, owner string, FOREIGN KEY (rawName) REFERENCES cardDefinition(imageFilename), FOREIGN KEY (owner) REFERENCES user(userid), PRIMARY KEY (id))");
		statement.execute("CREATE TABLE IF NOT EXISTS cardInfoField (keyName string UNIQUE, questionFormat string NOT NULL, PRIMARY KEY (keyName))");
		statement.execute("CREATE TABLE IF NOT EXISTS cardInfoEntry (id integer PRIMARY KEY AUTOINCREMENT, card string NOT NULL, field string NOT NULL, value string NOT NULL, FOREIGN KEY (card) REFERENCES cardDefinition(imageFilename), FOREIGN KEY (field) REFERENCES cardInfoField(keyName))");
		
		statement.execute("CREATE TABLE IF NOT EXISTS settings (dropNumCards int NOT NULL, dropCooldownMillis int NOT NULL, dungeonOptions int NOT NULL, dungeonCooldownMillis int NOT NULL, serverPort int NOT NULL, botPrefix string NOT NULL, siteUrl string NOT NULL, cardsFolder string NOT NULL, authHandler string NOT NULL, botToken string NOT NULL, botClientId string NOT NULL)");
		if (statement.executeQuery("SELECT COUNT(*) as count FROM settings").getInt("count") == 0)
		{
			statement.execute("INSERT INTO settings (dropNumCards, dropCooldownMillis, dungeonOptions, dungeonCooldownMillis, serverPort, botPrefix, siteUrl, cardsFolder, authHandler, botToken, botClientId) VALUES (3, 600000, 4, 600000, 28002, ',', 'drops.0k.rip', '/www/drops.0k.rip/card/', 'auth.aws1.0k.rip', 'INVALID_TOKEN_REPLACE_ME', 'INVALID_CLIENT_ID_REPLACE_ME')");
		}
		
		ResultSet settingsRS = statement.executeQuery("SELECT * FROM settings LIMIT 1");
		settingsRS.next();
		
		dropNumCards = settingsRS.getInt("dropNumCards");
		dropCooldownMillis = settingsRS.getInt("dropCooldownMillis");
		dungeonNumOptions = settingsRS.getInt("dungeonOptions");
		dungeonCooldownMillis = settingsRS.getInt("dungeonCooldownMillis");
		botPrefix = settingsRS.getString("botPrefix");
		botClientId = settingsRS.getString("botClientId");
		final String authHandlerUrl = settingsRS.getString("authHandler");
		final String cardsFolder = settingsRS.getString("cardsFolder");
		
		HttpServer server = new HttpServer(settingsRS.getInt("serverPort"));
		final String botToken = settingsRS.getString("botToken");
		
		ResultSet cardPackRS = statement.executeQuery("SELECT * from cardPack");
		while (cardPackRS.next())
		{
			String packName = cardPackRS.getString("packName");
			cardPacks.put(packName, new ArrayList<String>());
			System.out.println("Loaded info for cardpack " + packName);
		}
		
		ResultSet cardDefinitionRS = statement.executeQuery("SELECT * FROM cardDefinition");
		while (cardDefinitionRS.next())
		{
			String cardID = cardDefinitionRS.getString("imageFilename");
			HashMap<String,ArrayList<String>> inf = new HashMap<String,ArrayList<String>>();
			cardInfo.put(cardID, inf);
			String displayName = cardDefinitionRS.getString("displayName");
			if (displayName != null)
			{
				ArrayList<String> dnAL = new ArrayList<String>();
				dnAL.add(displayName);
				inf.put("display_name", dnAL);
			}
			String displayDescription = cardDefinitionRS.getString("displayDescription");
			if (displayDescription != null)
			{
				ArrayList<String> dnDSC = new ArrayList<String>();
				dnDSC.add(displayDescription);
				inf.put("display_description", dnDSC);
			}
			String packName = cardDefinitionRS.getString("packName");
			ArrayList<String> dnCAT = new ArrayList<String>();
			dnCAT.add(packName);
			inf.put("category", dnCAT);
			cardPacks.get(packName).add(cardID);
			
			ResultSet cardInfoRS = statement.executeQuery("SELECT * FROM cardInfoEntry WHERE card = '" + cardID  + "'");
			while (cardInfoRS.next())
			{
				String field = cardInfoRS.getString("field");
				if (inf.get(field) == null)
					inf.put(field, new ArrayList<String>());
				inf.get(field).add(cardInfoRS.getString("value"));
			}
			
			System.out.println("Loaded definition for card " + getCardDisplayName(cardID));
		}
		final ArrayList<String> cards = new ArrayList<String>(cardInfo.keySet());
		
		ResultSet cardInstanceRS = statement.executeQuery("SELECT * FROM cardInstance");
		while (cardInstanceRS.next())
		{
			String owner = cardInstanceRS.getString("owner");
			if (inventory.get(owner) == null)
				inventory.put(owner, new ArrayList<String>());
			inventory.get(owner).add(cardInstanceRS.getString("rawName") + ((char)4) + cardInstanceRS.getString("id") + ((char)4) + cardInstanceRS.getString("stars") + ((char)4) + cardInstanceRS.getString("level"));
			idLookup.put(cardInstanceRS.getString("id"), true);
		}
		System.out.println("Loaded card instance info for " + idLookup.keySet().size() + " cards");
		
		ResultSet userRS = statement.executeQuery("SELECT * FROM user");
		int userNum = 0;
		while (userRS.next())
		{
			userNum++;
			String userID = userRS.getString("userid");
			long dropT = userRS.getLong("lastDropTime");
			dropTime.put(userID, dropT>0?dropT:null);
			long dgT = userRS.getLong("lastDungeonTime");
			dgTime.put(userID, dgT>0?dgT:null);
		}
		System.out.println("Loaded user info for " + userNum + " users");
		
		ResultSet cardInfoFieldRS = statement.executeQuery("SELECT keyName, questionFormat FROM cardInfoField");
		int cieNum = 0, cioNum = 0;
		while (cardInfoFieldRS.next())
		{
			String field = cardInfoFieldRS.getString("keyName");
			dgCatMap.put(field, cardInfoFieldRS.getString("questionFormat"));
			ResultSet cardInfoRS = statement.executeQuery("SELECT * FROM cardInfoEntry WHERE field = '" + field + "'");
			dgopts.put(field, new ArrayList<String>());
			while (cardInfoRS.next())
			{
				dgopts.get(field).add(cardInfoRS.getString("value"));
				cieNum++;
			}
			cioNum++;
		}
		System.out.println("Loaded dungeon info: " + cieNum + " entries in " + cioNum + " fields");
		
		try
		{
			AuthHandler auth = new AuthHandler(authHandlerUrl);
			auth.registerCallbackEndpoint("/auth/callback");
			server.accept((req) -> {
				if (auth.handle(req, "drops-admin"))
					return;
				if (req.matches(HttpVerb.GET, "/") || req.matches(HttpVerb.GET, "/index.html"))
				{
					req.respond("<body>"
						+ "<a href=\"/admin/settings\">General Settings</a><br>"
						+ "<a href=\"/admin/cardpacks\">View / edit card packs</a><br>"
						+ "<a href=\"/admin/cards\">View / edit cards</a><br>"
						+ "<a href=\"https://discordapp.com/api/oauth2/authorize?client_id=" + botClientId + "&permissions=243336208192&scope=bot\">Add drops bot to a Discord server</a><br>"
					+ "</body>");
					return;
				}
				if (!auth.enforceValidCredentials("drops-admin"))
					return;
				if (req.matches(HttpVerb.GET, "/cardpack"))
				{
					try
					{
						String[] packCards = req.parameters.split("\\Q&nonce=\\E")[0].split("\\+");
						System.out.println(req.parameters.split("\\Q&nonce=\\E")[0]);
						byte[] data = renderedImage.get(req.parameters.split("\\Q&nonce=\\E")[0]);
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
				}
				else if (req.matches(HttpVerb.GET, "/admin/cardpacks"))
				{
					String resp = "<body>";
					resp += "<a href=\"/admin/cardpacks/add\">Create a new cardpack</a><br>";
					resp += "<h1>Card packs</h1>";
					for (String pack : cardPacks.keySet())
					{
						resp += "<a href=\"/admin/cardpack?name=" + pack + "\">" + pack + " (" + cardPacks.get(pack).size() + " cards)</a><br>";
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
						statement.execute("INSERT INTO cardpack (packName) VALUES ('" + cardPackName + "')");
						cardPacks.put(cardPackName, new ArrayList<String>());
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
					for (String card : cardPacks.get(pack))
					{
						resp += "<a href-\"/admin/card?name=" + card + "\">" + getCardDisplayName(card) + "</a><br>";
					}
					resp += "</body>";
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.GET, "/admin/cards"))
				{
					String resp = "<body>";
					for (String card : cardInfo.keySet())
					{
						resp += "<a href=\"/admin/card?name=" + card + "\">" + getCardDisplayName(card) + "</a><br>";
					}
					resp += "</body>";
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.GET, "/admin/cards/add"))
				{
					String defaultPack = req.getParam("pack").length==0?"":req.getParam("pack")[0];
					String packsOptions = "";
					for (String pack : cardPacks.keySet())
						packsOptions += "<option value=\"" + pack + "\"" + (pack.equals(defaultPack)?" selected":"") + ">" + pack + "</option>";
					String resp = "<body>"
						+ "<form enctype=\"multipart/form-data\" action=\"/admin/cards/add" + (defaultPack.length()==0?"":"?pack=" + defaultPack) + "\" method=\"post\">"
							+ "Display name: <input name=\"displayName\"><br>"
							+ "Display description: <input name=\"displayDescription\"><br>"
							+ "Card pack: <select name=\"cardPack\">" + packsOptions + "</select>"	// value=\"" + defaultPack + "\"><br>"
							+ "Card Image: <input name=\"cardImage\" type=\"file\">"
							+ "<input type=\"submit\" value=\"Create card!\"/>"
						+ "</form>"
						+ "<a href=\"/admin/" + (defaultPack.length()>0?"cardpack?name=" + defaultPack:"cards") + "\">Cancel</a>"
						+ "</body>";
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
						Files.write(Paths.get(cardsFolder + File.separator + rawName), fileDesc.filedata);
						String displayName = new String(req.getMultipart("displayName")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						if (displayName.length() == 0)
							displayName = SysUtils.toTitleCase(rawName);
						statement.execute("INSERT INTO cardDefinition (imageFilename, displayName, displayDescription, packName) VALUES ('"
							+ rawName + "',"
							+ "'" + displayName + "',"
							+ "'" + displayDescription + "',"
							+ "'" + cardPack + "'"
							+ "')");
						cardInfo.put(rawName, new HashMap<String,ArrayList<String>>());
						cardInfo.get(rawName).put("display_name", list(displayName));
						cardInfo.get(rawName).put("display_description", list(displayDescription));
						cardInfo.get(rawName).put("category", list(cardPack));
						cards.add(rawName);
						cardPacks.get(cardPack).add(rawName);
						String redirectURL = (req.getParam("pack").length==0 ? "/admin/card?name="+rawName : "/admin/cardpack?name=" + cardPack);
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"" + redirectURL + "\">" + redirectURL + "</a>",
							"Location: " + redirectURL
						);
					}
					catch (SQLException|IOException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/card"))
				{
					String card = req.getParam("name")[0];
					String cardDGInfo = "";
					for (String key : getCardDungeonCategories(card))
						cardDGInfo += key + ": " + cardInfo.get(card).get(key) + "<br>";
					req.respond("<body>"
						+ "<img src=/card/" + card + "/><br>"
						+ "<h2>General Info</h2>"
						+ "Display Name: " + getCardDisplayName(card) + "<br>"
						+ "Display Description: " + getCardDescription(card) + "<br>"
						+ "Card Pack: " + cardInfo.get(card).get("category") + "<br>"
						+ "<h2>Dungeon Info</h2>"
						+ cardDGInfo
						+ "</body>"
					);
					req.respond(HttpStatus.NOT_FOUND_404);
				}
				else if (req.matches(HttpVerb.POST, "/admin/card"))
				{
					// TODO
					// String card = req.getParam("name")[0];
					// String displayName = new String(req.getMultipart("display_name")[0].filedata, java.nio.charset.StandardCharsets.UTF_8);
					// if (displayName.trim().length() == 0)
						// displayName = null;
					// req.respond(HttpStatus.TEMPORARY_REDIRECT_302, "Location: /admin/card?name=" + card);
					req.respond(HttpStatus.NOT_FOUND_404);
				}
				else if (req.matches(HttpVerb.GET, "/admin/infofield"))
				{
					// TODO
					req.respond(HttpStatus.NOT_FOUND_404);
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield"))
				{
					// TODO
					req.respond(HttpStatus.NOT_FOUND_404);
				}
				else if (req.matches(HttpVerb.GET, "/admin/settings"))
				{
					try
					{
						ResultSet settingsRS2 = statement.executeQuery("SELECT * FROM settings LIMIT 1");
						settingsRS2.next();
						String resp = "<body>";
						resp += "<form enctype=\"multipart/form-data\" action=\"/admin/settings\" method=\"post\">";
						resp += "<h1>Settings</h1>";
						resp += "<h2>Drops</h2>";
						resp += "Number of cards to drop at once: <input name=\"dropNumCards\" value=\"" + settingsRS2.getInt("dropNumCards") + "\"/><br>";
						resp += "Drop cooldown (milliseconds): <input name=\"dropCooldownMillis\" value=\"" + settingsRS2.getInt("dropCooldownMillis") + "\"/><br>";
						resp += "<h2>Dungeon</h2>";
						resp += "Number of dungeon answers to choose from: <input name=\"dungeonOptions\" value=\"" + settingsRS2.getInt("dungeonOptions") + "\"/><br>";
						resp += "Dungeon cooldown (milliseconds): <input name=\"dungeonCooldownMillis\" value=\"" + settingsRS2.getInt("dungeonCooldownMillis") + "\"/><br>";
						resp += "<h2>Bot Config</h2>";
						resp += "Bot prefix: <input name=\"botPrefix\" value=\"" + settingsRS2.getString("botPrefix") + "\"/><br>";
						resp += "Bot client ID: <input name=\"botClientId\" value=\"" + settingsRS2.getString("botClientId") + "\"/><br>";
						resp += "Bot token (RESTART REQUIRED): <input name=\"botToken\" value=\"" + settingsRS2.getString("botToken") + "\"/><br>";
						resp += "<h2>App Config (RESTART REQUIRED for all)</h2>";
						resp += "Local Port: <input name=\"serverPort\" value=\"" + settingsRS2.getInt("serverPort") + "\"/><br>";
						resp += "Public URL: <input name=\"siteUrl\" value=\"" + settingsRS2.getString("siteUrl") + "\"/><br>";
						resp += "Auth Handler Public URL: <input name=\"authHandler\" value=\"" + settingsRS2.getString("authHandler") + "\"/><br>";
						resp += "Card Images Folder: <input name=\"cardsFolder\" value=\"" + settingsRS2.getString("cardsFolder") + "\"/><br>";
						resp += "<input type=\"submit\" value=\"Save changes\"/>";
						resp += "</form>";
						resp += "<a href=\"/\">Cancel</a>";
						resp += "</body>";
						req.respond(resp);
					}
					catch (SQLException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/settings"))
				{
					try
					{
						int dropNumCardsCandidate = Integer.parseInt(new String(req.getMultipart("dropNumCards")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						int dropCooldownMillisCandidate = Integer.parseInt(new String(req.getMultipart("dropCooldownMillis")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						int dungeonNumOptionsCandidate = Integer.parseInt(new String(req.getMultipart("dungeonOptions")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						int dungeonCooldownMillisCandidate = Integer.parseInt(new String(req.getMultipart("dungeonCooldownMillis")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim());
						String botPrefixCandidate = new String(req.getMultipart("botPrefix")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						String botClientIdCandidate = new String(req.getMultipart("botClientId")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim();
						statement.execute("UPDATE settings SET "
							+ "dropNumCards = " + dropNumCardsCandidate + ","
							+ "dropCooldownMillis = " + dropCooldownMillisCandidate + ","
							+ "dungeonOptions = " + dungeonNumOptionsCandidate + ","
							+ "dungeonCooldownMillis = " + dungeonCooldownMillisCandidate + ","
							+ "serverPort = " + Integer.parseInt(new String(req.getMultipart("serverPort")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim()) + ","
							+ "botPrefix = '" + botPrefixCandidate + "',"
							+ "botClientId = '" + botClientIdCandidate + "',"
							+ "botToken = '" + new String(req.getMultipart("botToken")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim() + "',"
							+ "siteUrl = '" + new String(req.getMultipart("siteUrl")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim() + "',"
							+ "authHandler = '" + new String(req.getMultipart("authHandler")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim() + "',"
							+ "cardsFolder = '" + new String(req.getMultipart("cardsFolder")[0].filedata, java.nio.charset.StandardCharsets.UTF_8).trim() + "'"
						);
						dropNumCards = dropNumCardsCandidate;
						dropCooldownMillis = dropCooldownMillisCandidate;
						dungeonNumOptions = dungeonNumOptionsCandidate;
						dungeonCooldownMillis = dungeonCooldownMillisCandidate;
						botPrefix = botPrefixCandidate;
						botClientId = botClientIdCandidate;
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
			System.out.println("Note: The Discord bot will *NOT* be started.");
			System.exit(0);
		}
		
		try
		{
			final DiscordClient client = DiscordClient.create(botToken);
			final GatewayDiscordClient gateway = client.login().block();
			
			gateway.on(MessageCreateEvent.class).subscribe(event -> {
				final Message message = event.getMessage();
				if ("~help".replaceAll("\\~", botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
				{
					final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
					channel.createMessage("Commands:\n  ~drop\n  ~inv\n  ~view [id]\n  ~dg\n  ~cd\n  ~help".replaceAll("\\~", botPrefix)).block();
				}
				if ("~drop".replaceAll("\\~", botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
				{
					final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
					Long userLastDrop = dropTime.get(message.getAuthor().orElseThrow(null).getId().asString());
					if (userLastDrop != null && System.currentTimeMillis()-userLastDrop < dropCooldownMillis)
					{
						channel.createMessage("Sorry " + message.getAuthor().orElseThrow(null).asMember(channel.getGuild().block().getId()).block().getDisplayName() +", you need to wait another " + formatDuration(dropCooldownMillis-(System.currentTimeMillis()-userLastDrop)) + " to drop :(").subscribe();
						return;
					}
					dropTime.put(message.getAuthor().orElseThrow(null).getId().asString(), System.currentTimeMillis());
					String cardStr = "", cardStrS = "";
					for (int i = 0; i < dropNumCards; i++)
					{
						String cardn = cards.get((int)(Math.random()*cards.size()));
						String[] cardInf = genCard(cardn);
						cardStr += (i>0?"+":"")+cardInf[0] + ((char)4) + cardInf[1] + ((char)4) + cardInf[2] + ((char)4) + cardInf[3];
						cardStrS += (i>0?"+":"")+cardn;
					}
					final String cardStrF = cardStr;
					final String cardStrFS = cardStrS;
					try
					{
						riOwner.put(message.getAuthor().orElseThrow(null).getId().asString(), cardStrFS);
						BufferedImage combined = stitchImages(cardStrFS.split("\\+"));
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ImageIO.write(combined, "webp", baos);
						renderedImage.put(cardStrFS, baos.toByteArray());
						System.out.println(cardStrFS);
					}
					catch (Exception ex){ex.printStackTrace();}
					channel.createEmbed(spec -> {
						var temp = spec.setColor(Color.RED)
						// .setAuthor("setAuthor", "https://drops.0k.rip", "https://drops.0k.rip/img/botprofile.png")
						.setThumbnail(message.getAuthor().orElseThrow(null).getAvatarUrl())
						.setTitle("Drops for " + message.getAuthor().orElseThrow(null).asMember(channel.getGuild().block().getId()).block().getDisplayName() + ":")
						.setDescription("Pick a card to keep:");
						for (int i = 0; i < dropNumCards; i++)
						{
							temp = temp.addField("Card " + (i+1), getCardDisplayName(cardStrFS.split("\\+")[i]), true);
						}
						temp.setImage("https://drops.0k.rip/cardpack?" + cardStrFS)
						.setFooter("drops?", "https://drops.0k.rip/img/botprofile.png")
						.setTimestamp(Instant.now());
					}).flatMap(msg -> {String author = message.getAuthor().orElseThrow(null).getId().asString(); dropWaiting.put(author, new String[]{((Message)msg).getId().asString(), cardStrF}); dropChannel.put(author, channel);
					var temp = msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93})));
					for (int i = 1; i < dropNumCards; i++)
						temp = temp.then(msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}))));
					return temp;
					}).subscribe();
				}
				if ("~inv".replaceAll("\\~", botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
				{
					final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
					String authoru = message.getAuthor().orElseThrow().asMember(channel.getGuild().block().getId()).block().getDisplayName();
					String author = message.getAuthor().orElseThrow().getId().asString();
					String cardsStr = "";
					if (inventory.get(author) != null)
					{
						for (String s : inventory.get(author))
						{
							String[] cardparts = s.split(""+(char)4);
							cardsStr += " - " + getCardDisplayName(cardparts[0]) + " [" + cardparts[1] + "] (" + cardparts[2] + " stars, level " + cardparts[3] + ")\n";
						}
					}
					channel.createMessage(authoru + "'s Inventory:\n" + cardsStr).block();
				}
				if ("~view".replaceAll("\\~", botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
				{
					final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
					String id = message.getContent().split(" ")[1].trim();
					for (ArrayList<String> cardlist : inventory.values())
						for (String card : cardlist)
						{
							String[] parts = card.split(""+(char)4);
							if (parts[1].equals(id))
							{
								channel.createEmbed(spec ->
									spec.setTitle(getCardDisplayName(parts[0]))
									.setDescription(getCardDescription(parts[0]))
									.addField("ID", parts[1], true)
									.addField("Stars", parts[2], true)
									.addField("Level", parts[3], true)
									.setImage("https://drops.0k.rip/card/" + parts[0])
									.setFooter("drops?", "https://drops.0k.rip/img/botprofile.png")
									.setTimestamp(Instant.now())
								).subscribe();
								return;
							}
						}
					channel.createMessage("Sorry " + message.getAuthor().orElseThrow().asMember(channel.getGuild().block().getId()).block().getDisplayName() +", I couldn't find card \"" + id + "\" :(").subscribe();
					return;
				}
				if ("~dg".replaceAll("\\~", botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
				{
					final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
					Long userLastDrop = dgTime.get(message.getAuthor().orElseThrow(null).getId().asString());
					if (userLastDrop != null && System.currentTimeMillis()-userLastDrop < dungeonCooldownMillis)
					{
						channel.createMessage("Sorry " + message.getAuthor().orElseThrow(null).asMember(channel.getGuild().block().getId()).block().getDisplayName() +", you need to wait another " + formatDuration(dungeonCooldownMillis-(System.currentTimeMillis()-userLastDrop)) + " to attempt a dungeon :(").subscribe();
						return;
					}
					dgTime.put(message.getAuthor().orElseThrow(null).getId().asString(), System.currentTimeMillis());
					while (true)
					{
						String cardn = cards.get((int)(Math.random()*cards.size()));
						List<String> dglist = new ArrayList<String>(dgopts.keySet());
						ArrayList<String> possibleCategories = getCardDungeonCategories(cardn);
						for (int i = 0; i < possibleCategories.size(); i++)
						{
							if (dgopts.get(possibleCategories.get(i)).size() < dungeonNumOptions)
							{
								possibleCategories.remove(i);
								i--;
							}
						}
						if (possibleCategories.size() != 0)
						{
							String category = possibleCategories.get((int)(Math.random()*possibleCategories.size()));
							ArrayList<String> correctOptns = cardInfo.get(cardn).get(category);
							String correctOptn = correctOptns.get((int)(Math.random()*correctOptns.size()));
							String[] opts = new String[dungeonNumOptions];
							int correctIndex = (int)(Math.random()*opts.length);
							opts[correctIndex] = correctOptn;
							ArrayList<String> possibleChoices = dgopts.get(category);
							for (int i = 0; i < correctOptns.size(); i++)
								possibleChoices.remove(correctOptns.get(i));
							if (possibleChoices.size()+1 < dungeonNumOptions)
								continue;
							Collections.shuffle(possibleChoices);
							for (int i = 0; i < opts.length; i++)
							{
								if (i == correctIndex)
									continue;
								opts[i] = possibleChoices.get(0);
								possibleChoices.remove(0);
							}
							
							channel.createEmbed(spec -> {
								var temp = spec.setColor(Color.RED)
								// .setAuthor("setAuthor", "https://drops.0k.rip", "https://drops.0k.rip/img/botprofile.png")
								.setThumbnail(message.getAuthor().orElseThrow(null).getAvatarUrl())
								.setTitle("Dungeon for " + message.getAuthor().orElseThrow(null).asMember(channel.getGuild().block().getId()).block().getDisplayName() + ":")
								.setDescription(getCardDisplayName(cardn) + " - " + (dgCatMap.get(category)==null?category:dgCatMap.get(category)));
								for (int i = 0; i < opts.length; i++)
								{
									temp = temp.addField(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}), opts[i], true);
								}
								temp.setImage("https://drops.0k.rip/card/" + cardn)
								.setFooter("drops?", "https://drops.0k.rip/img/botprofile.png")
								.setTimestamp(Instant.now());
							}).flatMap(msg -> {
								String author = message.getAuthor().orElseThrow(null).getId().asString();
								String[] optssend = new String[opts.length+1];
								optssend[0] = ((Message)msg).getId().asString();
								System.arraycopy(Arrays.stream(opts).map(o -> o.equals(correctOptn)?cardn:null).toArray(String[]::new), 0, optssend, 1, optssend.length-1);
								dgWaiting.put(author, optssend);
								var temp = msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93})));
								for (int i = 1; i < opts.length; i++)
									temp = temp.then(msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}))));
								return temp;
							}).subscribe();
							
							return;
						}
					}
				}
				if ("~cd".replaceAll("\\~", botPrefix).equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
				{
					final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
					String authoru = message.getAuthor().orElseThrow().asMember(channel.getGuild().block().getId()).block().getDisplayName();
					String author = message.getAuthor().orElseThrow().getId().asString();
					long dropCd = dropTime.get(author) == null ? 0 : dropCooldownMillis - (System.currentTimeMillis()-dropTime.get(author));
					long dungeonCd = dgTime.get(author) == null ? 0 : dungeonCooldownMillis - (System.currentTimeMillis()-dgTime.get(author));
					channel.createEmbed(spec ->
								spec.setTitle("Cooldowns")
									.setDescription("for " + authoru)
									.setThumbnail(message.getAuthor().orElseThrow(null).getAvatarUrl())
									.addField("Drop", (dropCd <= 0 ? "[READY]" : formatDuration(dropCd)), false)
									.addField("Dungeon", (dungeonCd <= 0 ? "[READY]" : formatDuration(dungeonCd)), true)
									.setFooter("drops?", "https://drops.0k.rip/img/botprofile.png")
									.setTimestamp(Instant.now())
					).subscribe();
				}
			});
			
			gateway.getEventDispatcher().on(ReactionAddEvent.class).subscribe(
				event -> {
					try
					{
						event.getEmoji().asUnicodeEmoji().orElseThrow();
					}
					catch (Exception ex)
					{
						return;
					}
					System.out.println("Found new reaction: " + event.getEmoji().asUnicodeEmoji().orElseThrow().getRaw() + " by " + event.getUserId() + " on " + event.getMessageId().asString());
					String author = event.getUserId().asString();
					String[] waitinfo = dropWaiting.get(author);
					System.out.println("Found waitinfo " + waitinfo);
					if (waitinfo != null && waitinfo[0].equals(event.getMessageId().asString()))
					{
						System.out.println("User and message ids match! Now check if the emoji matches...");
						String raw = event.getEmoji().asUnicodeEmoji().orElseThrow().getRaw();
						if (inventory.get(author) == null)
							inventory.put(author, new ArrayList<String>());
						String cardinfo = null;
						byte[] b = raw.getBytes();
						System.out.println(Arrays.toString(b));
						if (b.length == 7 && b[1] == (byte)-17 && b[2] == (byte)-72 && b[3] == (byte)-113 && b[4] == (byte)-30 && b[5] == (byte)-125 && b[6] == (byte)-93)
						{
							System.out.println("ROUGHLY OK");
							cardinfo = waitinfo[1].split("\\+")[raw.charAt(0)-0x31];
						}
						if (cardinfo != null)
						{
							String[] cardparts = cardinfo.split(""+(char)4);
							try
							{
								statement.execute("INSERT INTO cardInstance(rawName, id, level, stars, owner) VALUES ('" + cardparts[0] + "', '" + cardparts[1] + "', '" + cardparts[3] + "', '" + cardparts[2] + "', '" + author + "')");
								dropChannel.get(author).createMessage("Enjoy your new " + cardparts[2] + " star " + getCardDisplayName(cardparts[0]) + " (level " + cardparts[3] + ")").subscribe();
								dropWaiting.remove(author);
								if (inventory.get(author) == null)
									inventory.put(author, new ArrayList<String>());
								inventory.get(author).add(cardinfo);
								idLookup.put(cardparts[1], true);
								// saveMap(inventory, "/www/drops.0k.rip/inventory.dropdata");
							}
							catch (SQLException ex)
							{
								event.getChannel().block().createMessage("Sorry " + event.getUser().block().asMember(event.getGuild().block().getId()).block().getDisplayName() + ", the server encountered an error while processing your request.\n" + ex.getMessage()).subscribe();
							}
							renderedImage.remove(riOwner.get(author));
							riOwner.remove(author);
						}
					}
					else if (dgWaiting.get(author) != null && dgWaiting.get(author)[0].equals(event.getMessageId().asString()))
					{
						String raw = event.getEmoji().asUnicodeEmoji().orElseThrow().getRaw();
						byte[] b = raw.getBytes();
						if (b.length == 7 && b[1] == (byte)-17 && b[2] == (byte)-72 && b[3] == (byte)-113 && b[4] == (byte)-30 && b[5] == (byte)-125 && b[6] == (byte)-93)
						{
							String cardInfo = dgWaiting.get(author)[raw.charAt(0)-0x31+1];
							dgWaiting.remove(author);
							if (cardInfo != null)
							{
								try
								{
									String[] cardparts = genCard(cardInfo);
									statement.execute("INSERT INTO cardInstance(rawName, id, level, stars, owner) VALUES ('" + cardparts[0] + "', '" + cardparts[1] + "', '" + cardparts[3] + "', '" + cardparts[2] + "', '" + author + "')");
									if (inventory.get(author) == null)
										inventory.put(author, new ArrayList<String>());
									cardInfo = cardparts[0] + ((char)4) + cardparts[1] + ((char)4) + cardparts[2] + ((char)4) + cardparts[3];
									inventory.get(author).add(cardInfo);
									idLookup.put(cardparts[1], true);
									// saveMap(inventory, "/www/drops.0k.rip/inventory.dropdata");
									event.getChannel().block().createMessage("Enjoy your new " + cardparts[2] + " star " + getCardDisplayName(cardparts[0]) + " (level " + cardparts[3] + ")").subscribe();
								}
								catch (SQLException ex)
								{
									event.getChannel().block().createMessage("Sorry " + event.getUser().block().asMember(event.getGuild().block().getId()).block().getDisplayName() + ", the server encountered an error while processing your request.\n" + ex.getMessage()).subscribe();
								}
							}
							else
							{
								event.getChannel().block().createMessage("Sorry " + event.getUser().block().asMember(event.getGuild().block().getId()).block().getDisplayName() + ", that was wrong.\nBetter luck next time <3").subscribe();
							}
						}
					}
				}
			);
			System.out.println("The Discord bot should be up and running!");
			gateway.onDisconnect().block();
		}
		catch (Exception ex)
		{
			System.out.println("Uh-oh! Unhandled exception in the Discord bot code. botToken: " + botToken);
			ex.printStackTrace();
			System.out.println("Note: The web-server will continue running.");
		}
		
	}
	// public static void saveMap(Map<String,ArrayList<String>> map, String fileName)
	// {
		// String str = "";
		// for (Map.Entry<String,ArrayList<String>> e : map.entrySet())
		// {
			// str += e.getKey() + (char)2;
			// for (String s : e.getValue())
				// str += s + (char)1;
			// str += (char)0;
		// }
		// SysUtils.writeFile(new File(fileName), str, java.nio.charset.StandardCharsets.UTF_8);
	// }
	// public static Map<String,ArrayList<String>> loadMap(String fileName)
	// {
		// Map<String,ArrayList<String>> ret = new HashMap<String,ArrayList<String>>();
		// if (!new File(fileName).exists())
			// return ret;
		// String[] entryStrs = SysUtils.readTextFile(new File(fileName), java.nio.charset.StandardCharsets.UTF_8).split(""+(char)0);
		// for (int j = 0; j < entryStrs.length-1; j++)
		// {
			// System.out.println("A");
			// String entryStr = entryStrs[j];
			// ArrayList<String> list = new ArrayList<String>();
			// ret.put(entryStr.split(""+(char)2)[0], list);
			// if (entryStr.indexOf(""+(char)2)!=-1)
			// {
				// try{
				// String[] subentryStrs = entryStr.split(""+(char)2)[1].split(""+(char)1);
				// for (int i = 0; i < subentryStrs.length; i++)
				// {
					// System.out.println("B");
					// list.add(subentryStrs[i]);
				// }}catch(Exception ex){}
			// }
		// }
		// return ret;
	// }
	public static <T> ArrayList<T> list(T... items)
	{
		return new ArrayList<T>(Arrays.asList(items));
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
			System.out.println("Read " + card);
			BufferedImage img = ImageIO.read(new File("/www/drops.0k.rip/card/" + card));
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
	
	// asol.jpeg --> Aurelion Sol
	public static String getCardDisplayName(String rawName)
	{
		return cardInfo.get(rawName)==null || cardInfo.get(rawName).get("display_name") == null?SysUtils.toTitleCase(rawName.split("\\.")[0]):cardInfo.get(rawName).get("display_name").get(0).trim();
	}
	public static String getCardDescription(String rawName)
	{
		return cardInfo.get(rawName)==null || cardInfo.get(rawName).get("display_description") == null?"":cardInfo.get(rawName).get("display_description").get(0).trim();
	}
	public static ArrayList<String> getCardDungeonCategories(String rawName)
	{
		ArrayList<String> ret = new ArrayList<String>();
		if (cardInfo.get(rawName) == null)
			return ret;
		for (String prop : cardInfo.get(rawName).keySet())
		{
			if (prop.startsWith("dg_"))
				ret.add(prop);
		}
		return ret;
	}
	
	public static String[] genCard(String rawName)
	{
		String id = null;
		byte[] idbytes = new byte[4];
		do
		{
			rand.nextBytes(idbytes);
			id = SysUtils.encodeBase64(idbytes).replaceAll("[^a-zA-Z0-9]","").toLowerCase();
		}
		while (idLookup.get(id) != null);
		return new String[]{rawName, id, ""+((int)Math.ceil(Math.pow(Math.random(),10)*4)), ""+1};
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
class Card
{
	String rawName;
	String id;
	int level;
	int stars;
	HashMap<String,String[]> info;
	User owner;
	public void writeToDB()
	{
		
	}
	public static Card readFromDB()
	{
		return null;
	}
}
class Inventory
{
	User owner;
	ArrayList<Card> cards;
}
class User
{
	String userID;
	String username;
	Inventory inventory;
}


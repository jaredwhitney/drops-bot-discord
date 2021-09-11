import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import rip.$0k.http.*;
import rip.$0k.utils.SysUtils;

class WebServer
{
	public static final int WEB_SERVER_STARTUP_TIMES_TO_RETRY = 3;
	private final Random rand = new Random();
	
	private DatabaseManager dm;
	
	Map<String,RenderedImageStorage> renderedImageCache = new HashMap<String,RenderedImageStorage>();
	private String cardHTML, settingsHTML, cardPackHTML, infoFieldHTML, accountHTML;
	private byte[] botProfile;
	
	public WebServer(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	public void start()
	{
		try
		{
			HttpServer server = null;
			for (int i = 0; server == null && (i < (WEB_SERVER_STARTUP_TIMES_TO_RETRY+1)); i++)
			{
				try
				{
					server = new HttpServer(dm.settings.serverPort);
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					if (i == WEB_SERVER_STARTUP_TIMES_TO_RETRY)
					{
						System.out.println("Web server init failed, terminating the program. (attempt " + (i+1) + "/" + (WEB_SERVER_STARTUP_TIMES_TO_RETRY+1) + ").");
						System.out.println("Note: neither the web server nor the Discord bot will run, the entire program is being terminated.");
						System.exit(0);
					}
					try
					{
						System.out.println("Web server init failed, waiting 1 second before trying again. (attempt " + (i+1) + "/" + (WEB_SERVER_STARTUP_TIMES_TO_RETRY+1) + ").");
						Thread.sleep(1000);
					}
					catch (Exception ex2)
					{
						ex2.printStackTrace();
					}
				}
			}
			AuthHandler auth = new AuthHandler(dm.settings.authHandler);
			auth.maxlifetime = -1;	// don't let auth tokens time out to prevent people editing forms from losing their work
			auth.registerCallbackEndpoint("/auth/callback");
			
			cardHTML = DropsBot.readResourceToString("cards.html");
			settingsHTML = DropsBot.readResourceToString("settings.html");
			cardPackHTML = DropsBot.readResourceToString("cardpacks.html");
			infoFieldHTML = DropsBot.readResourceToString("infofields.html");
			accountHTML = DropsBot.readResourceToString("account.html");
			botProfile = DropsBot.readResource("botprofile.png");
			
			server.accept((req) -> {
				System.out.println("Request from " + req.domain + " remote addr " + req.getRemoteAddress() + " for url " + req.url);
				if (auth.handle(req, "drops-admin"))
					return;
				if (req.matches(HttpVerb.GET, "/card/.*"))
				{
					if (req.path.contains(".."))
					{
						req.respond(HttpStatus.NOT_FOUND_404);
						return;
					}
					Path filePath = Paths.get(dm.settings.cardsFolder, req.path.substring("/card/".length())).toAbsolutePath();
					if (!filePath.startsWith(Paths.get(dm.settings.cardsFolder).toAbsolutePath()) || !filePath.toFile().exists())
					{
						req.respond(HttpStatus.NOT_FOUND_404);
						return;
					}
					req.respondWithFile(filePath);
					return;
				}
				else if (req.matches(HttpVerb.GET, "/img/botprofile.png") || req.matches(HttpVerb.GET, "/favicon.ico"))
				{
					req.respond("image/png", botProfile);
					return;
				}
				else if (req.matches(HttpVerb.GET, "/") || req.matches(HttpVerb.GET, "/index.html"))
				{
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"/admin/cards\">/admin/cards</a>",
						"Location: /admin/cards"
					);
					return;
				}
				else if (req.matches(HttpVerb.GET, "/cardpack"))
				{
					try
					{
						String[] packCards = req.parameters.split("\\Q&nonce=\\E")[0].split("\\+");
						System.out.println(req.parameters.split("\\Q&nonce=\\E")[0]);
						var dataContainer = renderedImageCache.get(req.parameters.split("\\Q&nonce=\\E")[0]);
						byte[] data = null;
						if (dataContainer == null)
						{
							BufferedImage combined = Utils.stitchImages(dm, packCards);
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							ImageIO.write(combined, "webp", baos);
							data = baos.toByteArray();
						}
						else
						{
							data = dataContainer.cachedData;
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
				boolean localUserBypass = false;
				if (req.isLocal())
				{
					auth.username = "Local User Bypass";
					localUserBypass = true;
				}
				else if (!auth.enforceValidCredentials("drops-admin"))
					return;
				if (req.matches(HttpVerb.GET, "/admin/cardpacks"))
				{
					String data = "var data = {\n";
					for (CardPack pack : dm.cardPacks.values())
					{
						data += "\t\"" + escapeString(pack.packName) + "\": " + pack.cards.size() + ",\n";
					}
					data += "};";
					String resp = cardPackHTML
								.replaceAll("\\Q<<>>DATA_LOC<<>>\\E", data)
								.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/cardpacks/add"))
				{
					try
					{
						String cardPackName = req.getMultipart("packName").asString();
						CardPack cardPack = new CardPack(dm);
						cardPack.packName = cardPackName;
						cardPack.handleAdd();
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/cardpacks\">/admin/cardpacks</a>",
							"Location: /admin/cardpacks"
						);
					}
					catch (SQLException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/cardpacks/remove"))
				{
					try
					{
						String cardPackName = req.getMultipart("packName").asString();
						CardPack cardPack = dm.cardPacks.get(cardPackName);
						cardPack.handleRemove();
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/cardpacks\">/admin/cardpacks</a>",
							"Location: /admin/cardpacks"
						);
					}
					catch (SQLException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/cards"))
				{
					String datajson = "var data = {\n";
					for (CardDef card : dm.cardDefinitions.values())
					{
						datajson += "\t\"" + escapeString(card.imageFilename) + "\": {\n";
						datajson += "\t\t\"displayName\": \"" + escapeString(card.displayName) + "\",\n";
						if (card.displayDescription != null)
							datajson += "\t\t\"displayDescription\": \"" + escapeString(card.displayDescription) + "\",\n";
						datajson += "\t\t\"image\": \"https://" + escapeString(dm.settings.siteUrl) + "/card/" + escapeString(card.imageFilename) + "\",\n";
						datajson += "\t\t\"cardPack\": \"" + escapeString(card.cardPack.packName) + "\",\n";
						datajson += "\t\t\"extraInfo\": [\n";
						for (ArrayList<CardInfoFieldEntry> entryList : card.info.values())
						{
							for (CardInfoFieldEntry entry : entryList)
								datajson += "\t\t\t{ \"id\": \"" + entry.id + "\", \"key\": \"" + escapeString(entry.field.keyName) + "\", \"value\": \"" + escapeString(entry.value) + "\" },\n";
						}
						datajson += "\t\t]\n";
						datajson += "},\n";
					}
					datajson += "}\n";
					datajson += "var keys = [\n";
					for (CardInfoField field : dm.cardInfoFields.values())
					{
						datajson += "\t{ \"raw\": \"" + escapeString(field.keyName) + "\", \"display\": \"" + escapeString(field.questionFormat) + "\" },\n";
					}
					datajson += "]\n";
					datajson += "var packs = [\n";
					for (CardPack pack : dm.cardPacks.values())
					{
						datajson += "\t\"" + escapeString(pack.packName) + "\",\n";
					}
					datajson += "]";
					String resp = cardHTML
								.replaceAll("\\Q<<>>DATALOC<<>>\\E", datajson)
								.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/cards/add"))
				{
					try
					{
						String displayDescription = req.getMultipart("displayDescription").asString();
						String cardPack = req.getMultipart("cardPack").asString();
						HttpRequest.Multipart fileDesc = req.getMultipart("cardImage");
						String rawName = SysUtils.stripDangerousCharacters(fileDesc.filename.substring(0, fileDesc.filename.lastIndexOf("."))) + "." + SysUtils.stripDangerousCharacters(fileDesc.filename.substring(fileDesc.filename.lastIndexOf(".")));
						String displayName = req.getMultipart("displayName").asString();
						if (displayName.length() == 0)
							displayName = SysUtils.toTitleCase(rawName);
						
						CardDef card = new CardDef(dm);
						card.imageFilename = rawName;
						card.displayName = displayName;
						card.displayDescription = displayDescription;
						card.cardPack = dm.cardPacks.get(cardPack);
						
						System.out.println("Uploaded card was " + fileDesc.filedata);
						Path destPath = Paths.get(dm.settings.cardsFolder, rawName);
						if (!destPath.startsWith(Paths.get(dm.settings.cardsFolder).toAbsolutePath()))
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						Files.write(destPath, fileDesc.filedata);
						card.handleAdd();
						
						String redirectURL = (req.getParam("pack")==null ? "/admin/cards?name="+rawName : "/admin/cardpack?name=" + cardPack);
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"" + redirectURL + "\">" + redirectURL + "</a>",
							"Location: " + redirectURL
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/cards/edit"))
				{
					try
					{
						String displayDescription = req.getMultipart("displayDescription").asString();
						String cardPack = req.getMultipart("cardPack").asString();
						String rawName = req.getMultipart("imageFilename").asString();
						String displayName = req.getMultipart("displayName").asString();
						if (displayName.length() == 0)
							displayName = SysUtils.toTitleCase(rawName);
						
						CardDef card = dm.cardDefinitions.get(rawName);
						var cardOld = card.clone();
						card.imageFilename = rawName;
						card.displayName = displayName;
						card.displayDescription = displayDescription;
						card.cardPack = dm.cardPacks.get(cardPack);
						
						card.handleUpdate(cardOld);
						
						HashMap<String,Boolean> infoFieldPresent = new HashMap<String,Boolean>();
						for (Map.Entry<String,ArrayList<HttpRequest.Multipart>> multipart : req.multiparts.entrySet())
						{
							String key = multipart.getKey();
							System.out.println("key >" + key + "<");
							if (key.contains("extra-info-key-"))
							{
								String keyId = key.substring("extra-info-key-".length());
								System.out.println("keyId >" + keyId + "<");
								String keyName = multipart.getValue().get(0).asString();
								System.out.println("keyName >" + keyName + "<");
								String keyValue = req.getMultipart("extra-info-value-"+keyId).asString();
								System.out.println("keyValue >" + keyValue + "<");
								if (keyId.contains("NEW_ASSIGN"))
								{
									System.out.println("key was a NEW_ASSIGN entry");
									// Handle adding a new info entry
									CardInfoFieldEntry entry = new CardInfoFieldEntry(dm);
									byte[] idbytes = new byte[16];
									do
									{
										rand.nextBytes(idbytes);
										entry.id = SysUtils.encodeBase64(idbytes).replaceAll("[^a-zA-Z0-9]","").toLowerCase();
									} while (dm.cardInfoFields.get(entry.id) != null);
									entry.card = card;
									entry.field = dm.cardInfoFields.get(keyName);
									entry.value = keyValue;
									
									entry.handleAdd();
									
									infoFieldPresent.put(entry.id, true);
								}
								else
								{
									System.out.println("updating an existing key >" + keyId + "<");
									
									// Handle updating an existing info entry
									CardInfoFieldEntry entry = dm.cardInfoFieldEntries.get(keyId);
									var entryOld = entry.clone();
									entry.field = dm.cardInfoFields.get(keyName);
									entry.value = keyValue;
									
									System.out.println(entryOld.id + " -> " + entry.id);
									System.out.println(entryOld.field + " -> " + entry.field);
									System.out.println(entryOld.value + " -> " + entry.value);
									entry.handleUpdate(entryOld);
									
									infoFieldPresent.put(entry.id, true);
								}
							}
						}
						System.out.println("Touched keys: " + Arrays.toString(infoFieldPresent.keySet().toArray(String[]::new)));
						
						ArrayList<ArrayList<CardInfoFieldEntry>> cardInfoCopy = new ArrayList<ArrayList<CardInfoFieldEntry>>();
						cardInfoCopy.addAll(card.info.values());
						for (ArrayList<CardInfoFieldEntry> entryList : cardInfoCopy)
						{
							for (CardInfoFieldEntry entry : (ArrayList<CardInfoFieldEntry>)entryList.clone())
							{
								if (infoFieldPresent.get(entry.id) == null)
								{
									System.out.println("removing an existing key >" + entry.id + "<");
									
									// Handle removing an info entry
									entry.handleRemove();
								}
							}
						}
						
						String redirectURL = (req.getParam("pack")==null ? "/admin/cards?name="+rawName : "/admin/cardpack?name=" + cardPack);
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"" + redirectURL + "\">" + redirectURL + "</a>",
							"Location: " + redirectURL
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/infofield"))
				{
					try
					{
						String data = "var data = {\n";
						for (CardInfoField field : dm.cardInfoFields.values())
						{
							data += "\t\"" + escapeString(field.keyName) + "\": { \"keyName\": \"" + escapeString(field.keyName) + "\", \"questionFormat\": \"" + escapeString(field.questionFormat) + "\", \"numEntries\": " + field.entries.size() + " },\n";
						}
						data += "};";
						String resp = infoFieldHTML
									.replaceAll("\\Q<<>>DATA_LOC<<>>\\E", data)
									.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
						req.respond(resp);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield/add"))
				{
					try
					{
						String keyName = req.getMultipart("keyName").asString();
						String questionFormat = req.getMultipart("questionFormat").asString();
						
						CardInfoField field = new CardInfoField(dm);
						field.keyName = keyName;
						field.questionFormat = questionFormat;
						
						field.handleAdd();
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/infofield\">/admin/infofield</a>",
							"Location: /admin/infofield"
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield/remove"))
				{
					try
					{
						String keyName = req.getMultipart("keyName").asString();
						
						CardInfoField field = dm.cardInfoFields.get(keyName);
						
						System.out.println("SQL Remove field >" + keyName + "<");
						field.removeFromDatabase();
						System.out.println("Objects Remove field >" + keyName + "<");
						field.removeFromObjects();
						System.out.println("Card Info Remove field >" + keyName + "<");
						dm.cardInfoFields.remove(field.keyName);
						System.out.println("Finish >" + keyName + "<");
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/infofield\">/admin/infofield</a>",
							"Location: /admin/infofield"
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.POST, "/admin/infofield/edit"))
				{
					try
					{
						String keyName = req.getMultipart("keyName").asString();
						String questionFormat = req.getMultipart("questionFormat").asString();
						
						CardInfoField field = dm.cardInfoFields.get(keyName);
						var oldField = field.clone();
						field.keyName = keyName;
						field.questionFormat = questionFormat;
						
						field.handleUpdate(oldField);
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/infofield\">/admin/infofield</a>",
							"Location: /admin/infofield"
						);
					}
					catch (Exception ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else if (req.matches(HttpVerb.GET, "/admin/settings"))
				{
					String resp = settingsHTML
								.replaceAll("\\Q<<>>dropNumCards<<>>\\E", dm.settings.dropNumCards+"")
								.replaceAll("\\Q<<>>dropCooldownMillis<<>>\\E", dm.settings.dropCooldownMillis+"")
								.replaceAll("\\Q<<>>dungeonOptions<<>>\\E", dm.settings.dungeonOptions+"")
								.replaceAll("\\Q<<>>dungeonCooldownMillis<<>>\\E", dm.settings.dungeonCooldownMillis+"")
								.replaceAll("\\Q<<>>botPrefix<<>>\\E", dm.settings.botPrefix)
								.replaceAll("\\Q<<>>botClientId<<>>\\E", dm.settings.botClientId)
								.replaceAll("\\Q<<>>botToken<<>>\\E", dm.settings.botToken)
								.replaceAll("\\Q<<>>serverPort<<>>\\E", dm.settings.serverPort+"")
								.replaceAll("\\Q<<>>siteUrl<<>>\\E", dm.settings.siteUrl)
								.replaceAll("\\Q<<>>authHandler<<>>\\E", dm.settings.authHandler)
								.replaceAll("\\Q<<>>cardsFolder<<>>\\E", dm.settings.cardsFolder)
								.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.GET, "/admin/account"))
				{
					String resp = accountHTML
								.replaceAll("\\Q<<>>USERNAME<<>>\\E", auth.username)
								.replaceAll("\\Q<<>>AUTH_URL<<>>\\E", (localUserBypass?("127.0.0.1:" + dm.settings.serverPort):auth.authPath))
								.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/account/logout"))
				{
					auth.logout();
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"/\">the homepage</a>",
						"Location: /"
					);
				}
				else if (req.matches(HttpVerb.POST, "/admin/settings"))
				{
					try
					{
						Settings newSettings = new Settings(dm);
						
						newSettings.dropNumCards = req.getMultipart("dropNumCards").asInt();
						newSettings.dropCooldownMillis = req.getMultipart("dropCooldownMillis").asInt();
						newSettings.dungeonOptions = req.getMultipart("dungeonOptions").asInt();
						newSettings.dungeonCooldownMillis = req.getMultipart("dungeonCooldownMillis").asInt();
						newSettings.botPrefix = req.getMultipart("botPrefix").asString();
						newSettings.botClientId = req.getMultipart("botClientId").asString();
						newSettings.botToken = req.getMultipart("botToken").asString();
						newSettings.serverPort = req.getMultipart("serverPort").asInt();
						newSettings.siteUrl = req.getMultipart("siteUrl").asString();
						newSettings.authHandler = req.getMultipart("authHandler").asString();
						newSettings.cardsFolder = req.getMultipart("cardsFolder").asString();
						
						newSettings.handleUpdate(dm.settings);
						
						dm.settings = newSettings;
						
						req.respondWithHeaders1(
							HttpStatus.TEMPORARY_REDIRECT_302,
							"Redirecting you to <a href=\"/admin/settings\">/admin/settings</a>",
							"Location: /admin/settings"
						);
					}
					catch (SQLException ex)
					{
						req.respond("Internal error: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
				else
				{
					req.respond(HttpStatus.NOT_FOUND_404);
				}
			});
			System.out.println("The web server should be up and running!\n\tLocal address: http://127.0.0.1:" + dm.settings.serverPort);
		}
		catch (Exception ex)
		{
			System.out.println("Uh-oh! Unhandled exception in the web server code.");
			ex.printStackTrace();
			System.out.println("Note: The Discord bot will not be started; if it has already started it will be stopped.");
			System.exit(0);
		}
	}
	
	public static String escapeString(String s)
	{
	  return s.replaceAll("\\Q\\\\E", "\\\\\\\\")
			  .replaceAll("\\Q\t\\E", "\\\\\\\\\t")
			  .replaceAll("\\Q\b\\E", "\\\\\\\\\b")
			  .replaceAll("\\Q\n\\E", "\\\\\\\\\n")
			  .replaceAll("\\Q\r\\E", "\\\\\\\\\r")
			  .replaceAll("\\Q\f\\E", "\\\\\\\\\f")
			  .replaceAll("\\Q\'\\E", "\\\\\\\\'")
			  .replaceAll("\\Q\"\\E", "\\\\\\\\\"");
	}
}

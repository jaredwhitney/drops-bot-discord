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
	
	HttpServer server;
	AuthHandler auth;
	PermissionIdentifier ADMIN_PERMISSION;
	Map<String,RenderedImageStorage> renderedImageCache = new HashMap<String,RenderedImageStorage>();
	Map<String,PendingLinkInfo> pendingLinkInfo = new HashMap<String,PendingLinkInfo>();
	private String cardHTML, settingsHTML, cardPackHTML, infoFieldHTML, accountHTML, notAdminHTML, linkmeHTML, linkmeSuccessHTML, linkmeRejectHTML;
	private byte[] botProfile;
	
	public WebServer(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	public void start()
	{
		try
		{
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
			auth = new AuthHandler(dm.settings.authHandler, dm.settings.authApplicationKeys);
			auth.maxlifetime = -1;	// don't let auth tokens time out to prevent people editing forms from losing their work
			auth.registerCallbackEndpoint("/auth/callback");
			PermissionIdentifier adminPermission = null;
			if (auth.authPath.length() > 0)
			{
				if (auth.getSigningKeys() == null)
				{
					System.out.println("Couldn't find any auth application keys, generating them now...");
					auth.generateKeys();
					dm.settings.authApplicationKeys = auth.getSigningKeys();
					dm.settings.updateInDatabase();
				}
				adminPermission = auth.getPermissionIdentifier("drops-admin");
				if (!auth.checkApplicationExists())
				{
					System.out.println("Application creation success: " + auth.registerApplication("drops? Bot"));
				}
				else
				{
					System.out.println("Application already exists!");
				}
				if (!auth.checkPermissionExists(adminPermission))
				{
					System.out.println("Admin permission creation success: " + auth.registerPermission(adminPermission));
				}
			}
			else
				System.out.println("No auth server found; skipping application / keys setup.");
			this.ADMIN_PERMISSION = adminPermission;
			final PermissionIdentifier ADMIN_PERMISSION = adminPermission;
			
			cardHTML = DropsBot.readResourceToString("cards.html");
			settingsHTML = DropsBot.readResourceToString("settings.html");
			cardPackHTML = DropsBot.readResourceToString("cardpacks.html");
			infoFieldHTML = DropsBot.readResourceToString("infofields.html");
			accountHTML = DropsBot.readResourceToString("account.html");
			notAdminHTML = DropsBot.readResourceToString("notAdmin.html");
			linkmeHTML = DropsBot.readResourceToString("linkme.html");
			linkmeSuccessHTML = DropsBot.readResourceToString("linkmesuccess.html");
			linkmeRejectHTML = DropsBot.readResourceToString("linkmerejected.html");
			botProfile = DropsBot.readResource("botprofile.png");
			
			server.accept((req) -> {
				System.out.println("Request from " + req.domain + " remote addr " + req.getRemoteAddress() + " for url " + req.url);
				if (auth.handle(req, dm.settings.siteUrl))
					return;
				if (req.matches(HttpVerb.GET, "/card/.*"))
				{
					try
					{
						if (req.path.contains(".."))
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						if (dm.settings.cardsFolder.length() == 0)
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						CardDef def = dm.cardDefinitions.get(req.path.substring("/card/".length()));
						if (def == null)
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						BufferedImage resp = Utils.getCardImage(dm, def);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ImageIO.write(resp, "webp", baos);
						req.respond("image/webp", baos.toByteArray());
					}
					catch (Exception ex)
					{
						req.respond(HttpStatus.NOT_FOUND_404);
					}
					return;
				}
				else if (req.matches(HttpVerb.GET, "/cardinst/.*"))
				{
					try
					{
						if (req.path.contains(".."))
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						if (dm.settings.cardsFolder.length() == 0)
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						CardInst inst = dm.cardInstances.get(req.path.substring("/cardinst/".length()));
						if (inst == null)
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						BufferedImage resp = Utils.getCardImage(dm, inst);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ImageIO.write(resp, "webp", baos);
						req.respond("image/webp", baos.toByteArray());
					}
					catch (Exception ex)
					{
						req.respond(HttpStatus.NOT_FOUND_404);
					}
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
				else if (req.matches(HttpVerb.POST, "/admin/account/logout") || req.matches(HttpVerb.GET, "/admin/account/logout"))
				{
					auth.logout();
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"/\">the homepage</a>",
						"Location: /"
					);
					return;
				}
				else if (req.matches(HttpVerb.POST, "/linkme/logout") || req.matches(HttpVerb.GET, "/linkme/logout"))
				{
					String name = req.getParam("name");
					auth.logout();
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"/linkme?name=" + name + "\">/linkme?name=" + name + "</a>",
						"Location: /linkme?name=" + name
					);
					return;
				}
				else if (req.matches(HttpVerb.GET, "/linkme") || req.matches(HttpVerb.GET, "/linkme/accept") || req.matches(HttpVerb.GET, "/linkme/reject"))
				{
					if (!auth.enforceValidCredentials(dm.settings.siteUrl))
						return;
					String name = req.getParam("name");
					PendingLinkInfo info = pendingLinkInfo.get(name);
					if (info == null)
					{
						System.out.println("linkme error: invalid link");
						req.respond(linkmeHTML
							.replaceAll("<<>>ERROR_MESSAGE<<>>", "Invalid link.")
						);
						return;
					}
					String pageHtml = linkmeHTML.replaceAll("<<>>DISCORD_USERNAME<<>>", escapeString(info.discordUsername))
												.replaceAll("<<>>DISCORD_HASH<<>>", escapeString(info.discordHash))
												.replaceAll("<<>>DISCORD_PROFILE_IMAGE<<>>", escapeString(info.discordProfileImage))
												.replaceAll("<<>>USERNAME<<>>", escapeString(info.username))
												.replaceAll("<<>>NAME<<>>", escapeString(name));
					if (!auth.username.equals(info.username))
					{
						System.out.println("linkme error: logged in as \"" + auth.username + "\" wanted \"" + info.username + "\"");
						req.respond(pageHtml
							.replaceAll("<<>>ERROR_MESSAGE<<>>", "Logged in as the wrong user! This request wants to link " + info.discordUsername + "#" + info.discordHash + " to " + info.username + ", but you are logged in as " + auth.username + ".")
						);
						return;
					}
					if (req.matches(HttpVerb.GET, "/linkme"))
					{
						System.out.println("linkme serve page");
						req.respond(pageHtml.replaceAll("<<>>ERROR_MESSAGE<<>>", ""));
						return;
					}
					if (req.matches(HttpVerb.GET, "/linkme/accept"))
					{
						System.out.println("linkme accept");
						boolean success = true;
						if (!auth.checkPermissionExists(info.linkPermission))
							success = auth.registerPermission(info.linkPermission);
						success = success && auth.grantPermission(info.linkPermission);
						if (success)
						{
							info.discordChannel.createMessage("Linked <@" + name + "> with zkrAuth account " + info.username).subscribe();
							req.respond(linkmeSuccessHTML.replaceAll("<<>>MESSAGE<<>>", "Linked account " + info.discordUsername + "#" + info.discordHash + " with zkrAuth account " + info.username + "."));
						}
						else
						{
							req.respond(pageHtml
								.replaceAll("<<>>ERROR_MESSAGE<<>>", "Link failed! This might be due to a server config issue, or an auth server issue.")
							);
						}
					}
					else if (req.matches(HttpVerb.GET, "/linkme/reject"))
					{
						req.respond(linkmeRejectHTML.replaceAll("<<>>MESSAGE<<>>", "Rejected linking of account " + info.discordUsername + "#" + info.discordHash + " and zkrAuth account " + info.username + ". No discord notification will be sent."));
					}
					pendingLinkInfo.remove(name);
					System.out.println("Linkme invalidated link");
					return;
				}
				boolean localUserBypass = false;
				if (req.isLocal())
				{
					auth.username = "Local User Bypass";
					localUserBypass = true;
				}
				else if (auth.authPath.length() == 0)
				{
					req.respond(HttpStatus.NOT_FOUND_404);
					return;
				}
				else
				{
					if (!auth.enforceValidCredentials(dm.settings.siteUrl))
						return;
					if (!auth.checkPermission(ADMIN_PERMISSION))
					{
						req.respond(notAdminHTML);
						return;
					}
				}
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
						datajson += "\t\t\"image\": \"" + escapeString(dm.settings.siteUrl) + "/card/" + escapeString(card.imageFilename) + "\",\n";
						datajson += "\t\t\"cardPack\": \"" + escapeString(card.cardPack.packName) + "\",\n";
						datajson += "\t\t\"cardRarityMultiplier\": " + card.cardRarityMultiplier + ",\n";
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
						if (dm.settings.cardsFolder.length() == 0)
						{
							req.respond("I can't add that card! You need to set a card folder at <a href=\"/admin/settings\">/admin/settings</a> first!");
							return;
						}
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
						card.cardRarityMultiplier = 1f;
						card.cardPack = dm.cardPacks.get(cardPack);
						
						Path destPath = Paths.get(dm.settings.cardsFolder, rawName);
						if (!destPath.startsWith(Paths.get(dm.settings.cardsFolder).toAbsolutePath()))
						{
							req.respond(HttpStatus.NOT_FOUND_404);
							return;
						}
						Files.write(destPath, fileDesc.filedata);
						System.out.println("Uploaded card image to " + destPath);
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
						float cardRarityMultiplier = req.getMultipart("cardRarityMultiplier").asFloat();
						if (displayName.length() == 0)
							displayName = SysUtils.toTitleCase(rawName);
						
						CardDef card = dm.cardDefinitions.get(rawName);
						var cardOld = card.clone();
						card.imageFilename = rawName;
						card.displayName = displayName;
						card.displayDescription = displayDescription;
						card.cardRarityMultiplier = cardRarityMultiplier;
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
					String[] admins = auth.listPermissionUsers(ADMIN_PERMISSION);
					String adminStr = "";
					if (admins != null)
						for (int i = 0; i < admins.length; i++)
							adminStr += (i>0?", ":"") + "\"" + escapeString(admins[i]) + "\"";
					String resp = settingsHTML
								.replaceAll("\\Q<<>>dropNumCards<<>>\\E", dm.settings.dropNumCards+"")
								.replaceAll("\\Q<<>>dropCooldownMillis<<>>\\E", dm.settings.dropCooldownMillis+"")
								.replaceAll("\\Q<<>>dungeonOptions<<>>\\E", dm.settings.dungeonOptions+"")
								.replaceAll("\\Q<<>>dungeonCooldownMillis<<>>\\E", dm.settings.dungeonCooldownMillis+"")
								.replaceAll("\\Q<<>>trainCooldownMillis<<>>\\E", dm.settings.trainCooldownMillis+"")
								.replaceAll("\\Q<<>>cardsNeededToMerge<<>>\\E", dm.settings.cardsNeededToMerge+"")
								.replaceAll("\\Q<<>>cardsNeededToFuse<<>>\\E", dm.settings.cardsNeededToFuse+"")
								.replaceAll("\\Q<<>>botPrefix<<>>\\E", escapeString(dm.settings.botPrefix))
								.replaceAll("\\Q<<>>botClientId<<>>\\E", escapeString(dm.settings.botClientId))
								.replaceAll("\\Q<<>>botToken<<>>\\E", escapeString(dm.settings.botToken))
								.replaceAll("\\Q<<>>serverPort<<>>\\E", dm.settings.serverPort+"")
								.replaceAll("\\Q<<>>siteUrl<<>>\\E", escapeString(dm.settings.siteUrl))
								.replaceAll("\\Q<<>>authHandler<<>>\\E", escapeString(dm.settings.authHandler))
								.replaceAll("\\Q<<>>cardsFolder<<>>\\E", escapeString(dm.settings.cardsFolder))
								.replaceAll("\\Q<<>>admins<<>>\\E", adminStr)
								.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.GET, "/admin/account"))
				{
					String resp = accountHTML
								.replaceAll("\\Q<<>>USERNAME<<>>\\E", auth.username)
								.replaceAll("\\Q<<>>BOT_ADD_URL<<>>\\E", "https://discordapp.com/api/oauth2/authorize?client_id=" + dm.settings.botClientId + "&permissions=243336208192&scope=bot");
					req.respond(resp);
				}
				else if (req.matches(HttpVerb.POST, "/admin/admins/add"))
				{
					auth.grantPermission(ADMIN_PERMISSION, req.getMultipart("adminAdd").asString());
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"/admin/settings\">/admin/settings</a>",
						"Location: /admin/settings"
					);
				}
				else if (req.matches(HttpVerb.POST, "/admin/admins/remove"))
				{
					auth.revokePermission(ADMIN_PERMISSION, req.getMultipart("adminRemove").asString());
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"/admin/settings\">/admin/settings</a>",
						"Location: /admin/settings"
					);
				}
				else if (req.matches(HttpVerb.POST, "/admin/settings"))
				{
					try
					{
						Settings newSettings = dm.settings.clone();
						
						newSettings.dropNumCards = req.getMultipart("dropNumCards").asInt();
						newSettings.dropCooldownMillis = req.getMultipart("dropCooldownMillis").asInt();
						newSettings.dungeonOptions = req.getMultipart("dungeonOptions").asInt();
						newSettings.dungeonCooldownMillis = req.getMultipart("dungeonCooldownMillis").asInt();
						newSettings.trainCooldownMillis = req.getMultipart("trainCooldownMillis").asInt();
						newSettings.cardsNeededToMerge = req.getMultipart("cardsNeededToMerge").asInt();
						newSettings.cardsNeededToFuse = req.getMultipart("cardsNeededToFuse").asInt(); 
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
				else if (req.matches(HttpVerb.POST, "/admin/restart"))
				{
					String redirectURL = (localUserBypass ? ("http://127.0.0.1:" + dm.settings.serverPort) : dm.settings.siteUrl);
					req.respondWithHeaders1(
						HttpStatus.TEMPORARY_REDIRECT_302,
						"Redirecting you to <a href=\"" + redirectURL + "\">" + redirectURL + "</a>",
						"Location: " + redirectURL
					);
					DropsBot.restart();
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
	
	public void shutdown()
	{
		server.shutdown();
	}
	
	public static String escapeString(String s)
	{
	  return s.replaceAll("\\\\", "\\\\\\\\\\\\\\\\")
			  .replaceAll("\\Q\t\\E", "\\\\\\\\\t")
			  .replaceAll("\\Q\b\\E", "\\\\\\\\\b")
			  .replaceAll("\\Q\n\\E", "\\\\\\\\\n")
			  .replaceAll("\\Q\r\\E", "\\\\\\\\\r")
			  .replaceAll("\\Q\f\\E", "\\\\\\\\\f")
			  .replaceAll("\\Q\'\\E", "\\\\\\\\'")
			  .replaceAll("\\Q\"\\E", "\\\\\\\\\"");
	}
}

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
import javax.imageio.*;
import java.awt.image.*;
public final class ExampleBot
{
	public static final int DROP_CARDS = 3;
	public static final int DROP_COOLDOWN_MS = 10 * TimeConstants.MILLISECONDS_PER_MINUTE;
	
	public static final int DG_OPTS = 3;
	public static final int DG_COOLDOWN_MS = 10 * TimeConstants.MILLISECONDS_PER_MINUTE;
	
	public static final String DATABASE_LOCATION = "./dropdatabase.db";
	
	public static final HashMap<String,String> dgCatMap = new HashMap<String,String>();
	static
	{
		dgCatMap.put("dg_first_appearance_tv", "First appearance on the show");
		dgCatMap.put("dg_first_appearance_game", "First appearance in the game");
		dgCatMap.put("dg_first_appearance_date", "First appearance (date)");
		dgCatMap.put("dg_unlock_cost_be", "Champion Blue Essence cost");
		dgCatMap.put("dg_ability_passive_name", "Passive ability");
		dgCatMap.put("dg_ability_q_name", "Q ability");
		dgCatMap.put("dg_ability_w_name", "W ability");
		dgCatMap.put("dg_ability_e_name", "E ability");
		dgCatMap.put("dg_ability_c_name", "c ability");
		dgCatMap.put("dg_ability_ult_name", "Ultimate ability");
		dgCatMap.put("dg_ability_shift_name", "SHIFT ability");
		dgCatMap.put("dg_real_name", "Real name");
		dgCatMap.put("dg_hero_power", "Hero power");
		dgCatMap.put("dg_age", "Age");
		dgCatMap.put("dg_origin_country", "Country of origin");
		dgCatMap.put("dg_category", "Category");
		dgCatMap.put("dg_affiliation", "Affiliated with");
		dgCatMap.put("dg_species", "Species");
	}
	
	public static Map<String,String> riOwner = new HashMap<String,String>();
	public static Map<String,String[]> dropWaiting = new HashMap<String,String[]>();
	public static Map<String,MessageChannel> dropChannel = new HashMap<String,MessageChannel>();
	public static Map<String,ArrayList<String>> inventory = new HashMap<String,ArrayList<String>>();
	public static Map<String,Boolean> idLookup = new HashMap<String,Boolean>();
	public static Map<String,Long> dropTime = new HashMap<String,Long>();
	public static Map<String,Long> dgTime = new HashMap<String,Long>();
	public static Map<String,byte[]> renderedImage = new HashMap<String,byte[]>();
	public static Map<String,HashMap<String,String[]>> cardInfo = new HashMap<String,HashMap<String,String[]>>();
	public static Map<String,ArrayList<String>> dgopts = new HashMap<String,ArrayList<String>>();
	public static Map<String,String[]> dgWaiting = new HashMap<String,String[]>();
	public static final Random rand = new Random();
	public static void main(final String[] args)
	{
		
		// Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_LOCATION);
		// Statement statement = connection.createStatement();
		// statement.execute("CREATE TABLE IF NOT EXISTS cards (rawName string, id string UNIQUE, level integer, stars integer, owner string)");
		// statement.execute("CREATE TABLE IF NOT EXISTS inventories (id integer autoincrement, ");
		
		HttpServer server = new HttpServer(28002);
		final String token = args[0];
		final DiscordClient client = DiscordClient.create(token);
		final GatewayDiscordClient gateway = client.login().block();
		
		final File[] cards = new File("/www/drops.0k.rip/card/").listFiles();
		
		inventory = loadMap("inventory.dropdata");
		for (ArrayList<String> cardlist : inventory.values())
			for (String card : cardlist)
				idLookup.put(card.split(""+(char)4)[1],true);
		
		for (File cardF : cards)
		{
			String name = cardF.getName().split("\\.")[0];
			try
			{
				String[] inflines = SysUtils.readTextFile(new File("/www/drops.0k.rip/cardinfo/" + name + ".txt"), java.nio.charset.StandardCharsets.UTF_8).split("\\n");
				HashMap<String,String[]> map = new HashMap<String,String[]>();
				cardInfo.put(cardF.getName(), map);
				for (String line : inflines)
				{
					map.put(line.split("\\=")[0].trim(),line.split("\\=")[1].trim().split("\\,"));
					if (line.startsWith("dg_"))
					{
						ArrayList<String> dglist = dgopts.get(line.split("\\=")[0].trim());
						if (dglist == null)
						{
							dglist = new ArrayList<String>();
							String opt = line.split("\\=")[0].trim();
							dgopts.put(opt, dglist);
						}
						String[] vals = line.split("\\=")[1].trim().split("\\,");
						for (String val : vals)
						{
							val = val.trim();
							boolean found = false;
							for (String s : dglist)
								if (s.equalsIgnoreCase(val))
								{
									found = true;
									break;
								}
							if (!found)
								dglist.add(val);
						}
					}
				}
				System.out.println("Read cardinfo for " + name);
			}
			catch (Exception ex){
			}
		}
		
		gateway.on(MessageCreateEvent.class).subscribe(event -> {
			final Message message = event.getMessage();
			if (",help".equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
			{
				final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
				channel.createMessage("Commands:\n  ,drop\n  ,inv\n  ,view [id]\n  ,dg\n  ,cd\n  ,help").block();
			}
			if (",drop".equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
			{
				final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
				Long userLastDrop = dropTime.get(message.getAuthor().orElseThrow(null).getId().asString());
				if (userLastDrop != null && System.currentTimeMillis()-userLastDrop < DROP_COOLDOWN_MS)
				{
					channel.createMessage("Sorry " + message.getAuthor().orElseThrow(null).asMember(channel.getGuild().block().getId()).block().getDisplayName() +", you need to wait another " + formatDuration(DROP_COOLDOWN_MS-(System.currentTimeMillis()-userLastDrop)) + " to drop :(").subscribe();
					return;
				}
				dropTime.put(message.getAuthor().orElseThrow(null).getId().asString(), System.currentTimeMillis());
				String cardStr = "", cardStrS = "";
				for (int i = 0; i < DROP_CARDS; i++)
				{
					String cardn = cards[(int)(Math.random()*cards.length)].getName();
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
					for (int i = 0; i < DROP_CARDS; i++)
					{
						temp = temp.addField("Card " + (i+1), getCardDisplayName(cardStrFS.split("\\+")[i]), true);
					}
					temp.setImage("https://drops.0k.rip/cardpack?" + cardStrFS)
					.setFooter("drops?", "https://drops.0k.rip/img/botprofile.png")
					.setTimestamp(Instant.now());
				}).flatMap(msg -> {String author = message.getAuthor().orElseThrow(null).getId().asString(); dropWaiting.put(author, new String[]{((Message)msg).getId().asString(), cardStrF}); dropChannel.put(author, channel);
				var temp = msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93})));
				for (int i = 1; i < DROP_CARDS; i++)
					temp = temp.then(msg.addReaction(ReactionEmoji.unicode(new String(new byte[]{(byte)(0x31+i),(byte)-17,(byte)-72,(byte)-113,(byte)-30,(byte)-125,(byte)-93}))));
				return temp;
				}).subscribe();
			}
			if (",inv".equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
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
			if (",view".equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
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
			if (",dg".equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
			{
				final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
				Long userLastDrop = dgTime.get(message.getAuthor().orElseThrow(null).getId().asString());
				if (userLastDrop != null && System.currentTimeMillis()-userLastDrop < DG_COOLDOWN_MS)
				{
					channel.createMessage("Sorry " + message.getAuthor().orElseThrow(null).asMember(channel.getGuild().block().getId()).block().getDisplayName() +", you need to wait another " + formatDuration(DG_COOLDOWN_MS-(System.currentTimeMillis()-userLastDrop)) + " to attempt a dungeon :(").subscribe();
					return;
				}
				dgTime.put(message.getAuthor().orElseThrow(null).getId().asString(), System.currentTimeMillis());
				while (true)
				{
					String cardn = cards[(int)(Math.random()*cards.length)].getName();
					List<String> dglist = new ArrayList<String>(dgopts.keySet());
					ArrayList<String> possibleCategories = getCardDungeonCategories(cardn);
					for (int i = 0; i < possibleCategories.size(); i++)
					{
						if (dgopts.get(possibleCategories.get(i)).size() < DG_OPTS)
						{
							possibleCategories.remove(i);
							i--;
						}
					}
					if (possibleCategories.size() != 0)
					{
						String category = possibleCategories.get((int)(Math.random()*possibleCategories.size()));
						String[] correctOptns = cardInfo.get(cardn).get(category);
						String correctOptn = correctOptns[(int)(Math.random()*correctOptns.length)];
						String[] opts = new String[DG_OPTS];
						int correctIndex = (int)(Math.random()*opts.length);
						opts[correctIndex] = correctOptn;
						ArrayList<String> possibleChoices = dgopts.get(category);
						for (int i = 0; i < correctOptns.length; i++)
							possibleChoices.remove(correctOptns[i]);
						if (possibleChoices.size()+1 < DG_OPTS)
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
			if (",cd".equalsIgnoreCase(message.getContent().split(" ")[0].trim()))
			{
				final GuildMessageChannel channel = (GuildMessageChannel)message.getChannel().block();
				String authoru = message.getAuthor().orElseThrow().asMember(channel.getGuild().block().getId()).block().getDisplayName();
				String author = message.getAuthor().orElseThrow().getId().asString();
				long dropCd = dropTime.get(author) == null ? 0 : DROP_COOLDOWN_MS - (System.currentTimeMillis()-dropTime.get(author));
				long dungeonCd = dgTime.get(author) == null ? 0 : DG_COOLDOWN_MS - (System.currentTimeMillis()-dgTime.get(author));
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
		
		System.out.println("Hi, server *should* be up and running, and *NOT* returning 404 under any circumstances.");
		server.accept((req) -> {
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
			else
			{
				req.respond(HttpStatus.NOT_FOUND_404);
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
					// if (raw.equals("\u0031\uFE0F\u20E3"))
						// cardinfo = waitinfo[1].split("\\+")[0];
					// else if (raw.equals("\u0032\uFE0F\u20E3"))
						// cardinfo = waitinfo[1].split("\\+")[1];
					// else if (raw.equals("\u0033\uFE0F\u20E3"))
						// cardinfo = waitinfo[1].split("\\+")[2];
					if (cardinfo != null)
					{
						String[] cardparts = cardinfo.split(""+(char)4);
						dropChannel.get(author).createMessage("Enjoy your new " + cardparts[2] + " star " + getCardDisplayName(cardparts[0]) + " (level " + cardparts[3] + ")").subscribe();
						dropWaiting.remove(author);
						if (inventory.get(author) == null)
							inventory.put(author, new ArrayList<String>());
						inventory.get(author).add(cardinfo);
						idLookup.put(cardparts[1], true);
						saveMap(inventory, "inventory.dropdata");
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
							String[] cardparts = genCard(cardInfo);
							if (inventory.get(author) == null)
								inventory.put(author, new ArrayList<String>());
							cardInfo = cardparts[0] + ((char)4) + cardparts[1] + ((char)4) + cardparts[2] + ((char)4) + cardparts[3];
							inventory.get(author).add(cardInfo);
							idLookup.put(cardparts[1], true);
							saveMap(inventory, "inventory.dropdata");
							event.getChannel().block().createMessage("Enjoy your new " + cardparts[2] + " star " + getCardDisplayName(cardparts[0]) + " (level " + cardparts[3] + ")").subscribe();
						}
						else
						{
							event.getChannel().block().createMessage("Sorry " + event.getUser().block().asMember(event.getGuild().block().getId()).block().getDisplayName() + ", that was wrong.\nBetter luck next time <3").subscribe();
						}
					}
				}
			}
		);

		gateway.onDisconnect().block();
	}
	public static void saveMap(Map<String,ArrayList<String>> map, String fileName)
	{
		String str = "";
		for (Map.Entry<String,ArrayList<String>> e : map.entrySet())
		{
			str += e.getKey() + (char)2;
			for (String s : e.getValue())
				str += s + (char)1;
			str += (char)0;
		}
		SysUtils.writeFile(new File(fileName), str, java.nio.charset.StandardCharsets.UTF_8);
	}
	public static Map<String,ArrayList<String>> loadMap(String fileName)
	{
		Map<String,ArrayList<String>> ret = new HashMap<String,ArrayList<String>>();
		if (!new File(fileName).exists())
			return ret;
		String[] entryStrs = SysUtils.readTextFile(new File(fileName), java.nio.charset.StandardCharsets.UTF_8).split(""+(char)0);
		for (int j = 0; j < entryStrs.length-1; j++)
		{
			System.out.println("A");
			String entryStr = entryStrs[j];
			ArrayList<String> list = new ArrayList<String>();
			ret.put(entryStr.split(""+(char)2)[0], list);
			if (entryStr.indexOf(""+(char)2)!=-1)
			{
				try{
				String[] subentryStrs = entryStr.split(""+(char)2)[1].split(""+(char)1);
				for (int i = 0; i < subentryStrs.length; i++)
				{
					System.out.println("B");
					list.add(subentryStrs[i]);
				}}catch(Exception ex){}
			}
		}
		return ret;
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
		return cardInfo.get(rawName)==null || cardInfo.get(rawName).get("display_name") == null?SysUtils.toTitleCase(rawName.split("\\.")[0]):cardInfo.get(rawName).get("display_name")[0].trim();
	}
	public static String getCardDescription(String rawName)
	{
		return cardInfo.get(rawName)==null || cardInfo.get(rawName).get("display_description") == null?"":cardInfo.get(rawName).get("display_description")[0].trim();
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


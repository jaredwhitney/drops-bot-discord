import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import rip.$0k.utils.SysUtils;

public final class DropsBot
{
	public static final File DATABASE_LOCATION_FILE = new File("drops-db-path.cfg");
	
	public static DatabaseManager databaseManager;
	public static WebServer webServer;
	public static DiscordBot discordBot;
	static String[] argStore;
	
	/**
	 * Main program entry point, starts both the web server and the discord bot
	 */
	public static void main(final String[] args) throws SQLException
	{
		
		System.out.println("*** drops? bot main method called ***");
		
		// Used for software-initiated restart
		argStore = args;
		
		// Determine the location of the database file
		String databaseLocation = null;
		if (args.length > 0)
		{
			databaseLocation = args[0];
		}
		else
		{
			databaseLocation = SysUtils.readTextFile(DATABASE_LOCATION_FILE, UTF_8);
			if (databaseLocation == null)
			{
				System.err.println("Couldn't figure out where you wanted the database to be stored; pass the file path as an argument or write it to \"" + DATABASE_LOCATION_FILE.getAbsolutePath() + "\".");
				System.exit(0);
			}
			databaseLocation = databaseLocation.trim();
		}
		
		// Start the database manager
		databaseManager = new DatabaseManager();
		databaseManager.connectToDatabase(databaseLocation);
		databaseManager.initAllTables();
		databaseManager.readAllFromDatabase();
		
		// Start the web server
		webServer = new WebServer(databaseManager);
		webServer.start();
		
		// Start the discord bot
		discordBot = new DiscordBot(databaseManager, webServer);
		discordBot.start();
		
	}
	
	/**
	 * Reads a resource from the JAR file into a String
	 */
	public static String readResourceToString(String resourceName) throws IOException
	{
		return new String(readResource(resourceName), UTF_8);
	}
	
	/**
	 * Reads a resource from the JAR file into a byte array
	 */
	public static byte[] readResource(String resourceName) throws IOException
	{
		System.out.println("Read " + resourceName + " from the jar file");
		InputStream in = DropsBot.class.getResourceAsStream(resourceName); 
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		in.transferTo(os);
		return os.toByteArray();
	}
}

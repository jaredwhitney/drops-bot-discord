import java.sql.SQLException;
import java.sql.ResultSet;

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
	
	public Settings(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected Settings clone()
	{
		throw new RuntimeException("Settings cannot currently be cloned.");
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
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
	
	static void readFromDatabase(DatabaseManager dm) throws SQLException
	{
		ResultSet settingsRS = dm.connection.createStatement().executeQuery(
			"SELECT * FROM settings"
			+ " LIMIT 1"
		);
		if (!settingsRS.next())
		{
			dm.connection.createStatement().executeUpdate(
				"INSERT INTO settings ("
					+ "dropNumCards, dropCooldownMillis, dungeonOptions, dungeonCooldownMillis, serverPort, botPrefix, siteUrl, cardsFolder, authHandler, botToken, botClientId"
				+ ") VALUES ("
					+ "3, 600000, 4, 600000, 28002, ',', 'drops.0k.rip', '/www/drops.0k.rip/card/', 'auth.aws1.0k.rip', 'INVALID_TOKEN_REPLACE_ME', 'INVALID_CLIENT_ID_REPLACE_ME'"
				+ ")"
			);
			readFromDatabase(dm);
		}
		Settings settings = new Settings(dm);
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
		dm.settings = settings;
	}
	
	void addToDatabase() throws SQLException
	{
		throw new RuntimeException("Settings currently cannot be added.");
	}
	void addToObjects() {}
	void updateInDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
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

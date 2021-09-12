import java.sql.SQLException;
import java.sql.PreparedStatement;
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
			PreparedStatement statement = dm.connection.prepareStatement(
				"INSERT INTO settings ("
					+ "dropNumCards, dropCooldownMillis, dungeonOptions, dungeonCooldownMillis, serverPort, botPrefix, siteUrl, cardsFolder, authHandler, botToken, botClientId"
				+ ") VALUES ("
					+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"
				+ ")"
			);
			statement.setInt(1, 3);
			statement.setInt(2, 60000);
			statement.setInt(3, 4);
			statement.setInt(4, 600000);
			statement.setInt(5, 28002);
			statement.setString(6, ",");
			statement.setString(7, "drops.0k.rip");
			statement.setString(8, "/www/drops.0k.rip/card/");
			statement.setString(9, "auth.aws1.0k.rip");
			statement.setString(10, "INVALID_TOKEN_REPLACE_ME");
			statement.setString(11, "INVALID_CLIENT_ID_REPLACE_ME");
			statement.executeUpdate();
			readFromDatabase(dm);
			return;
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
		PreparedStatement statement = dm.connection.prepareStatement(
			"UPDATE settings SET "
				+ "dropNumCards = ?,"
				+ "dropCooldownMillis = ?,"
				+ "dungeonOptions = ?,"
				+ "dungeonCooldownMillis = ?,"
				+ "serverPort = ?,"
				+ "botPrefix = ?,"
				+ "siteUrl = ?,"
				+ "cardsFolder = ?,"
				+ "authHandler = ?,"
				+ "botToken = ?,"
				+ "botClientId = ?"
		);
		statement.setInt(1, dropNumCards);
		statement.setInt(2, dropCooldownMillis);
		statement.setInt(3, dungeonOptions);
		statement.setInt(4, dungeonCooldownMillis);
		statement.setInt(5, serverPort);
		statement.setString(6, botPrefix);
		statement.setString(7, siteUrl);
		statement.setString(8, cardsFolder);
		statement.setString(9, authHandler);
		statement.setString(10, botToken);
		statement.setString(11, botClientId);
		statement.executeUpdate();
	}
	void removeFromDatabase() throws SQLException
	{
		throw new RuntimeException("Settings currently cannot be deleted.");
	}
	void removeFromObjects() {}
}

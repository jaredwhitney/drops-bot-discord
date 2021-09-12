import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.sql.SQLException;

class DatabaseManager
{
	// populated from the database
	public Settings settings;
	public Map<String,User> users = new HashMap<String,User>();
	public Map<String,CardPack> cardPacks = new HashMap<String,CardPack>();
	public Map<String,CardDef> cardDefinitions = new HashMap<String,CardDef>();
	public Map<String,CardInst> cardInstances = new HashMap<String,CardInst>();
	public Map<String,CardInfoField> cardInfoFields = new HashMap<String,CardInfoField>();
	public Map<String,CardInfoFieldEntry> cardInfoFieldEntries = new HashMap<String,CardInfoFieldEntry>();
	public Connection connection;
	
	public void connectToDatabase(String databaseLocation) throws SQLException
	{
		Properties properties = new Properties();
		properties.setProperty("PRAGMA foreign_keys", "ON");
		connection = DriverManager.getConnection("jdbc:sqlite:" + new File(databaseLocation).getAbsolutePath(), properties);
	}
	public void disconnect()
	{
		try
		{
			connection.close();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	public void initAllTables() throws SQLException
	{
		User.tableInit(this);
		CardPack.tableInit(this);
		CardDef.tableInit(this);
		CardInst.tableInit(this);
		CardInfoField.tableInit(this);
		CardInfoFieldEntry.tableInit(this);
		Settings.tableInit(this);
	}
	public void readAllFromDatabase() throws SQLException
	{
		User.readAllFromDatabaseInit(this);
		CardPack.readAllFromDatabaseInit(this);
		CardDef.readAllFromDatabaseInit(this);
		CardInst.readAllFromDatabaseInit(this);
		CardInfoField.readAllFromDatabaseInit(this);
		CardInfoFieldEntry.readAllFromDatabaseInit(this);
		
		User.readAllFromDatabaseFinalize(this);
		CardPack.readAllFromDatabaseFinalize(this);
		CardDef.readAllFromDatabaseFinalize(this);
		CardInst.readAllFromDatabaseFinalize(this);
		CardInfoField.readAllFromDatabaseFinalize(this);
		CardInfoFieldEntry.readAllFromDatabaseFinalize(this);
		
		Settings.readFromDatabase(this);
	}
}

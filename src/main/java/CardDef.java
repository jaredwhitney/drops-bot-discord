import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

class CardDef extends DBEnabledClass
{
	String imageFilename;
	String displayName;
	String displayDescription;
	CardPack cardPack;
	
	Map<String,CardInst> instances = new HashMap<String,CardInst>();
	Map<CardInfoField,ArrayList<CardInfoFieldEntry>> info = new HashMap<CardInfoField,ArrayList<CardInfoFieldEntry>>();
	
	public CardDef(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected CardDef clone()
	{
		CardDef card = new CardDef(dm);
		card.imageFilename = imageFilename;
		card.displayName = displayName;
		card.displayDescription = displayDescription;
		card.cardPack = cardPack;
		card.instances.putAll(instances);
		card.info.putAll(info);
		return card;
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
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
	
	static void readAllFromDatabaseInit(DatabaseManager dm) throws SQLException
	{
		ResultSet cardDefinitionRS = dm.connection.createStatement().executeQuery("SELECT * FROM cardDefinition");
		while (cardDefinitionRS.next())
		{
			CardDef obj = new CardDef(dm);
			obj.imageFilename = cardDefinitionRS.getString("imageFilename");
			obj.displayName = cardDefinitionRS.getString("displayName");
			obj.displayDescription = cardDefinitionRS.getString("displayDescription");
			dm.cardDefinitions.put(obj.imageFilename, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(DatabaseManager dm) throws SQLException
	{
		ResultSet cardDefinitionRS = dm.connection.createStatement().executeQuery("SELECT * FROM cardDefinition");
		while (cardDefinitionRS.next())
		{
			CardDef obj = dm.cardDefinitions.get(cardDefinitionRS.getString("imageFilename"));
			
			obj.cardPack = dm.cardPacks.get(cardDefinitionRS.getString("packName"));
			
			obj.addToObjects();
		}
	}
	
	void handleAdd() throws SQLException
	{
		super.handleAdd();
		dm.cardDefinitions.put(imageFilename, this);
	}
	
	void addToDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"INSERT INTO cardDefinition ("
				+ "imageFilename, displayName, displayDescription, packName"
			+ ") VALUES ("
				+ "?, ?, ?, ?"
			+ ")"
		);
		statement.setString(1, imageFilename);
		statement.setString(2, displayName);
		statement.setString(3, displayDescription);
		statement.setString(4, cardPack.packName);
		statement.executeUpdate();
	}
	
	void addToObjects()
	{
		cardPack.cards.put(imageFilename, this);
	}
	
	void updateInDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"UPDATE cardDefinition SET "
				+ "displayName = ?,"
				+ "displayDescription = ?,"
				+ "packName = ?"
			+ " WHERE imageFilename = ?"
		);
		statement.setString(1, displayName);
		statement.setString(2, displayDescription);
		statement.setString(3, cardPack.packName);
		statement.setString(4, imageFilename);
		statement.executeUpdate();
	}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.cardDefinitions.remove(this);
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


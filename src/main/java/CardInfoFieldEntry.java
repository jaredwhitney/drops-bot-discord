import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

class CardInfoFieldEntry extends DBEnabledClass
{
	String id;
	CardDef card;
	CardInfoField field;
	String value;
	
	public CardInfoFieldEntry(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected CardInfoFieldEntry clone()
	{
		CardInfoFieldEntry entry = new CardInfoFieldEntry(dm);
		entry.id = id;
		entry.card = card;
		entry.field = field;
		entry.value = value;
		return entry;
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
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
	
	static void readAllFromDatabaseInit(DatabaseManager dm) throws SQLException
	{
		ResultSet cardInfoERS = dm.connection.createStatement().executeQuery("SELECT * FROM cardInfoEntry");
		while (cardInfoERS.next())
		{
			CardInfoFieldEntry obj = new CardInfoFieldEntry(dm);
			obj.id = cardInfoERS.getString("id");
			obj.value = cardInfoERS.getString("value");
			dm.cardInfoFieldEntries.put(obj.id, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(DatabaseManager dm) throws SQLException
	{
		ResultSet cardInfoERS = dm.connection.createStatement().executeQuery("SELECT * FROM cardInfoEntry");
		while (cardInfoERS.next())
		{
			CardInfoFieldEntry obj = dm.cardInfoFieldEntries.get(cardInfoERS.getString("id"));
				
			obj.card = dm.cardDefinitions.get(cardInfoERS.getString("card"));
			obj.field = dm.cardInfoFields.get(cardInfoERS.getString("field"));
			
			obj.addToObjects();
		}
	}
	
	void handleAdd() throws SQLException
	{
		super.handleAdd();
		dm.cardInfoFieldEntries.put(id, this);
	}
	
	void addToDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"INSERT INTO cardInfoEntry ("
				+ "id, card, field, value"
			+ ") VALUES ("
				+ "?, ?, ?, ?"
			+ ")"
		);
		statement.setString(1, id);
		statement.setString(2, card.imageFilename);
		statement.setString(3, field.keyName);
		statement.setString(4, value);
		statement.executeUpdate();
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
		PreparedStatement statement = dm.connection.prepareStatement(
			"UPDATE cardInfoEntry SET "
				+ "card = ?,"
				+ "field = ?,"
				+ "value = ?"
			+ " WHERE id = ?"
		);
		statement.setString(1, card.imageFilename);
		statement.setString(2, field.keyName);
		statement.setString(3, value);
		statement.setString(4, id);
		statement.executeUpdate();
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
		addToObjects();
	}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.cardInfoFieldEntries.remove(id);
	}
	
	void removeFromDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"DELETE FROM cardInfoEntry"
			+ " WHERE id = ?"
		);
		statement.setString(1, id);
		statement.executeUpdate();
	}
	
	void removeFromObjects()
	{
		field.entries.remove(id);
		card.info.get(field).remove(this);
	}
}

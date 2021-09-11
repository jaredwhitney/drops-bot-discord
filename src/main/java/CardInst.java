import java.sql.SQLException;
import java.sql.ResultSet;

class CardInst extends DBEnabledClass
{
	CardDef def;
	String id;
	int level;
	int stars;
	User owner;
	
	public CardInst(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected CardInst clone()
	{
		CardInst card = new CardInst(dm);
		card.def = def;
		card.id = id;
		card.level = level;
		card.stars = stars;
		card.owner = owner;
		return card;
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardInstance ("
				+ "rawName string NOT NULL,"
				+ "id string UNIQUE,"
				+ "level integer NOT NULL,"
				+ "stars integer NOT NULL,"
				+ "owner string,"
				+ "FOREIGN KEY (rawName)"
					+ " REFERENCES cardDefinition(imageFilename),"
				+ "FOREIGN KEY (owner)"
					+ " REFERENCES user(userid),"
				+ "PRIMARY KEY (id)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(DatabaseManager dm) throws SQLException
	{
		ResultSet cardInstanceRS = dm.connection.createStatement().executeQuery("SELECT * FROM cardInstance");
		while (cardInstanceRS.next())
		{
			CardInst obj = new CardInst(dm);
			obj.id = cardInstanceRS.getString("id");
			obj.level = cardInstanceRS.getInt("level");
			obj.stars = cardInstanceRS.getInt("stars");
			dm.cardInstances.put(obj.id, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(DatabaseManager dm) throws SQLException
	{
		ResultSet cardInstanceRS = dm.connection.createStatement().executeQuery("SELECT * FROM cardInstance");
		while (cardInstanceRS.next())
		{
			CardInst obj = dm.cardInstances.get(cardInstanceRS.getString("id"));
				
			obj.def = dm.cardDefinitions.get(cardInstanceRS.getString("rawName"));
			obj.owner = dm.users.get(cardInstanceRS.getString("owner"));
			
			obj.addToObjects();
		}
	}
	
	void handleAdd() throws SQLException
	{
		super.handleAdd();
		dm.cardInstances.put(id, this);
	}
	
	void addToDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"INSERT INTO cardInstance ("
				+ "rawName, id, level, stars, owner"
			+ ") VALUES ("
				+ "'" + def.imageFilename + "', '" + id + "', '" + level + "', '" + stars + "', '" + owner.userId + "'"
			+ ")"
		);
	}
	
	void addToObjects()
	{
		def.instances.put(id, this);
		owner.inventory.put(id, this);
	}
	
	void updateInDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"UPDATE cardInstance SET "
				+ "rawName = '" + def.imageFilename + "',"
				+ "level = '" + level + "',"
				+ "stars = '" + stars + "'"
				+ "owner = '" + owner.userId + "'"
			+ " WHERE id = '" + id + "'"
		);
	}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.cardInstances.remove(this);
	}
	
	void removeFromDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"DELETE FROM cardInstance"
			+ " WHERE id = '" + id + "'"
		);
	}
	
	void removeFromObjects()
	{
		def.instances.remove(id);
		owner.inventory.remove(id);
	}
}

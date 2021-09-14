import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

class CardInst extends DBEnabledClass
{
	CardDef def;
	String id;
	int level;
	int stars;
	User owner;
	boolean favorited;
	
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
		card.favorited = favorited;
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
				+ "favorited integer,"
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
			obj.favorited = cardInstanceRS.getInt("favorited")!=0;
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
		PreparedStatement statement = dm.connection.prepareStatement(
			"INSERT INTO cardInstance ("
				+ "rawName, id, level, stars, owner, favorited"
			+ ") VALUES ("
				+ "?, ?, ?, ?, ?, ?"
			+ ")"
		);
		statement.setString(1, def.imageFilename);
		statement.setString(2, id);
		statement.setInt(3, level);
		statement.setInt(4, stars);
		statement.setString(5, owner.userId);
		statement.setInt(6, favorited?1:0);
		statement.executeUpdate();
	}
	
	void addToObjects()
	{
		def.instances.put(id, this);
		owner.inventory.put(id, this);
	}
	
	void updateInDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"UPDATE cardInstance SET "
				+ "rawName = ?,"
				+ "level = ?,"
				+ "stars = ?,"
				+ "owner = ?,"
				+ "favorited = ?"
			+ " WHERE id = ?"
		);
		statement.setString(1, def.imageFilename);
		statement.setInt(2, level);
		statement.setInt(3, stars);
		statement.setString(4, owner.userId);
		statement.setInt(5, favorited?1:0);
		statement.setString(6, id);
		statement.executeUpdate();
	}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.cardInstances.remove(id);
	}
	
	void removeFromDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"DELETE FROM cardInstance"
			+ " WHERE id = ?"
		);
		statement.setString(1, id);
		statement.executeUpdate();
	}
	
	void removeFromObjects()
	{
		def.instances.remove(id);
		owner.inventory.remove(id);
	}
}

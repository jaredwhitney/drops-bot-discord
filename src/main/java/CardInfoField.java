import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

class CardInfoField extends DBEnabledClass
{
	String keyName;
	String questionFormat;
	
	Map<String,CardInfoFieldEntry> entries = new HashMap<String,CardInfoFieldEntry>();
	
	public CardInfoField(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected CardInfoField clone()
	{
		CardInfoField infoField = new CardInfoField(dm);
		infoField.keyName = keyName;
		infoField.questionFormat = questionFormat;
		infoField.entries.putAll(entries);
		return infoField;
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardInfoField ("
				+ "keyName string UNIQUE,"
				+ "questionFormat string NOT NULL,"
				+ "PRIMARY KEY (keyName)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(DatabaseManager dm) throws SQLException
	{
		ResultSet cardInfoRS = dm.connection.createStatement().executeQuery("SELECT * FROM cardInfoField");
		while (cardInfoRS.next())
		{
			CardInfoField obj = new CardInfoField(dm);
			obj.keyName = cardInfoRS.getString("keyName");
			obj.questionFormat = cardInfoRS.getString("questionFormat");
			dm.cardInfoFields.put(obj.keyName, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(DatabaseManager dm) throws SQLException
	{
		return;
	}
	
	void handleAdd() throws SQLException
	{
		super.handleAdd();
		dm.cardInfoFields.put(keyName, this);
	}
	
	void addToDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"INSERT INTO cardInfoField ("
				+ "keyName, questionFormat"
			+ ") VALUES ("
				+ "?, ?"
			+ ")"
		);
		statement.setString(1, keyName);
		statement.setString(2, questionFormat);
		statement.executeUpdate();
	}
	
	void addToObjects() {}
	
	void updateInDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"UPDATE cardInfoField SET "
				+ "questionFormat = ?"
			+ " WHERE keyName = ?"
		);
		statement.setString(1, questionFormat);
		statement.setString(2, keyName);
		statement.executeUpdate();
	}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.cardInfoFields.remove(this);
	}
	
	void removeFromDatabase() throws SQLException
	{
		if (entries.size() > 0)
			throw new RuntimeException("Not going to remove this info field definition: it's still being used by " + entries.size() + " cards!");
		PreparedStatement statement = dm.connection.prepareStatement(
			"DELETE FROM cardInfoField"
			+ " WHERE keyName = ?"
		);
		statement.setString(1, keyName);
		statement.executeUpdate();
	}
	
	void removeFromObjects() {}
	
}

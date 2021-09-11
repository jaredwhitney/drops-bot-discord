import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

class CardPack extends DBEnabledClass
{
	String packName;
	
	Map<String,CardDef> cards = new HashMap<String,CardDef>();
	
	public CardPack(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected CardPack clone()
	{
		CardPack pack = new CardPack(dm);
		pack.packName = packName;
		pack.cards.putAll(cards);
		return pack;
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS cardPack ("
				+ "packName string"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(DatabaseManager dm) throws SQLException
	{
		ResultSet cardPackRS = dm.connection.createStatement().executeQuery("SELECT * FROM cardPack");
		while (cardPackRS.next())
		{
			CardPack obj = new CardPack(dm);
			obj.packName = cardPackRS.getString("packName");
			dm.cardPacks.put(obj.packName, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(DatabaseManager dm) throws SQLException
	{
		return;
	}
	
	void handleAdd() throws SQLException
	{
		super.handleAdd();
		dm.cardPacks.put(packName, this);
	}
	
	void addToDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"INSERT INTO cardPack ("
				+ "packName"
			+ ") VALUES ("
				+ "'" + packName + "'"
			+ ")"
		);
	}
	
	void addToObjects() {}
	
	void updateInDatabase() throws SQLException
	{
		throw new RuntimeException("Card Packs currently cannot be updated.");
	}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.cardPacks.remove(this);
	}
	
	void removeFromDatabase() throws SQLException
	{
		if (cards.size() > 0)
			throw new RuntimeException("Not going to remove this card pack: it's still being used by " + cards.size() + " cards!");
		dm.connection.createStatement().executeUpdate(
			"DELETE FROM cardPack"
			+ " WHERE packName = '" + packName + "'"
		);
	}
	
	void removeFromObjects() {}
}

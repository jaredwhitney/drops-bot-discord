import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

class User extends DBEnabledClass
{
	String userId;
	long lastDropTime;
	long lastDungeonTime;
	
	Map<String,CardInst> inventory = new HashMap<String,CardInst>();
	
	public User(DatabaseManager databaseManager)
	{
		dm = databaseManager;
	}
	
	protected User clone()
	{
		User user = new User(dm);
		user.userId = userId;
		user.lastDropTime = lastDropTime;
		user.lastDungeonTime = lastDungeonTime;
		return user;
	}
	
	static void tableInit(DatabaseManager dm) throws SQLException
	{
		dm.connection.createStatement().execute(
			"CREATE TABLE IF NOT EXISTS user ("
				+ "userid string UNIQUE,"
				+ "lastDropTime long NOT NULL,"
				+ "lastDungeonTime long NOT NULL,"
				+ "PRIMARY KEY (userid)"
			+ ")"
		);
	}
	
	static void readAllFromDatabaseInit(DatabaseManager dm) throws SQLException
	{
		ResultSet userRS = dm.connection.createStatement().executeQuery("SELECT * FROM user");
		while (userRS.next())
		{
			User obj = new User(dm);
			obj.userId = userRS.getString("userid");
			obj.lastDropTime = userRS.getLong("lastDropTime");
			obj.lastDungeonTime = userRS.getLong("lastDungeonTime");
			dm.users.put(obj.userId, obj);
		}
	}
	
	static void readAllFromDatabaseFinalize(DatabaseManager dm) throws SQLException
	{
		return;
	}
	
	void handleAdd() throws SQLException
	{
		super.handleAdd();
		dm.users.put(userId, this);
	}
	
	void addToDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"INSERT INTO user ("
				+ "userid, lastDropTime, lastDungeonTime"
			+ ") VALUES ("
				+ "'" + userId + "', " + lastDropTime + ", " + lastDungeonTime
			+ ")"
		);
	}
	
	void addToObjects() {}
	
	void updateInDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"UPDATE user SET "
			+ "lastDropTime = " + lastDropTime + ", "
			+ "lastDungeonTime = " + lastDungeonTime
			+ " WHERE userid = '" + userId + "'"
		);
	}
	
	void updateInObjects(DBEnabledClass previous) {}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.users.remove(this);
	}
	
	void removeFromDatabase() throws SQLException
	{
		dm.connection.createStatement().executeUpdate(
			"DELETE FROM user"
			+ " WHERE userid = '" + userId + "'"
		);
	}
	
	void removeFromObjects() {}
}

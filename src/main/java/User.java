import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

class User extends DBEnabledClass
{
	String userId;
	long lastDropTime;
	long lastDungeonTime;
	long lastTrainTime;
	
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
				+ "lastTrainTime long NOT NULL,"
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
			obj.lastTrainTime = userRS.getLong("lastTrainTime");
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
		PreparedStatement statement = dm.connection.prepareStatement(
			"INSERT INTO user ("
				+ "userid, lastDropTime, lastDungeonTime, lastTrainTime"
			+ ") VALUES ("
				+ "?, ?, ?, ?"
			+ ")"
		);
		statement.setString(1, userId);
		statement.setLong(2, lastDropTime);
		statement.setLong(3, lastDungeonTime);
		statement.setLong(4, lastTrainTime);
		statement.executeUpdate();
	}
	
	void addToObjects() {}
	
	void updateInDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"UPDATE user SET "
			+ "lastDropTime = ?,"
			+ "lastDungeonTime = ?,"
			+ "lastTrainTime = ?"
			+ " WHERE userid = ?"
		);
		statement.setLong(1, lastDropTime);
		statement.setLong(2, lastDungeonTime);
		statement.setLong(3, lastTrainTime);
		statement.setString(4, userId);
		statement.executeUpdate();
	}
	
	void updateInObjects(DBEnabledClass previous) {}
	
	void handleRemove() throws SQLException
	{
		super.handleRemove();
		dm.users.remove(userId);
	}
	
	void removeFromDatabase() throws SQLException
	{
		PreparedStatement statement = dm.connection.prepareStatement(
			"DELETE FROM user"
			+ " WHERE userid = ?"
		);
		statement.setString(1, userId);
		statement.executeUpdate();
	}
	
	void removeFromObjects() {}
}

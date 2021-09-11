import java.sql.Connection;
import java.sql.SQLException;

abstract class DBEnabledClass
{
	DatabaseManager dm;
	void handleAdd() throws SQLException
	{
		addToDatabase();
		addToObjects();
	}
	abstract void addToDatabase() throws SQLException;
	abstract void addToObjects();
	void handleUpdate(DBEnabledClass previous) throws SQLException
	{
		updateInDatabase();
		updateInObjects(previous);
	}
	abstract void updateInDatabase() throws SQLException;
	void updateInObjects(DBEnabledClass previous)
	{
		if (previous != null)
		{
			previous.removeFromObjects();
			addToObjects();
		}
		else
		{
			throw new RuntimeException("Previous version of the object was not provided to updateInObjects()!");
		}
	}
	void handleRemove() throws SQLException
	{
		removeFromDatabase();
		removeFromObjects();
	}
	abstract void removeFromDatabase() throws SQLException;
	abstract void removeFromObjects();
	protected abstract DBEnabledClass clone();
}

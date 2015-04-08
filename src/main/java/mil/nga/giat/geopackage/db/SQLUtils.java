package mil.nga.giat.geopackage.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.nga.giat.geopackage.GeoPackageException;
import mil.nga.giat.geopackage.user.ContentValues;

/**
 * SQL Utility methods
 * 
 * @author osbornb
 */
public class SQLUtils {

	/**
	 * Logger
	 */
	private static final Logger log = Logger
			.getLogger(SQLUtils.class.getName());

	/**
	 * Execute the SQL
	 * 
	 * @param connection
	 * @param sql
	 */
	public static void execSQL(Connection connection, String sql) {
		Statement statement = null;
		try {
			statement = connection.createStatement();
			statement.execute(sql);
		} catch (SQLException e) {
			throw new GeoPackageException("Failed to execute SQL statement: "
					+ sql, e);
		} finally {
			closeStatement(statement, sql);
		}

	}

	/**
	 * Query for results
	 * 
	 * @param connection
	 * @param sql
	 * @param selectionArgs
	 * @return
	 */
	public static ResultSet query(Connection connection, String sql,
			String[] selectionArgs) {

		PreparedStatement statement = null;
		ResultSet resultSet = null;

		try {
			statement = connection.prepareStatement(sql);
			setArguments(statement, selectionArgs);
			resultSet = statement.executeQuery();
		} catch (SQLException e) {
			throw new GeoPackageException("Failed to execute SQL statement: "
					+ sql, e);
		} finally {
			if (resultSet == null) {
				closeStatement(statement, sql);
			}
		}

		return resultSet;
	}

	/**
	 * Attempt to count the results of the query
	 * 
	 * @param connection
	 * @param sql
	 * @param selectionArgs
	 * @return count if known, -1 if not able to determine
	 */
	public static int count(Connection connection, String sql,
			String[] selectionArgs) {

		if (!sql.toLowerCase().contains(" count(*) ")) {
			int index = sql.toLowerCase().indexOf(" from ");
			if (index == -1) {
				return -1;
			}
			sql = "select count(*)" + sql.substring(index);
		}

		ResultSet resultSet = query(connection, sql, selectionArgs);

		int count = 0;
		try {
			if (resultSet.next()) {
				count = resultSet.getInt(1);
			} else {
				throw new GeoPackageException("Failed to get count. SQL: "
						+ sql);
			}
		} catch (SQLException e) {
			throw new GeoPackageException("Failed to get count. SQL: " + sql, e);
		} finally {
			closeResultSetStatement(resultSet, sql);
		}

		return count;
	}

	/**
	 * Get the query count
	 * 
	 * @param connection
	 * @param table
	 * @param where
	 * @param args
	 * @return
	 */
	public static int count(Connection connection, String table, String where,
			String[] args) {
		StringBuilder countQuery = new StringBuilder();
		countQuery.append("select count(*) from ").append(table);
		if (where != null) {
			countQuery.append(" where ").append(where);
		}
		String sql = countQuery.toString();

		ResultSet resultSet = query(connection, sql, args);

		int count = 0;
		try {
			if (resultSet.next()) {
				count = resultSet.getInt(1);
			} else {
				throw new GeoPackageException("Failed to get count. SQL: "
						+ sql);
			}
		} catch (SQLException e) {
			throw new GeoPackageException("Failed to get count. SQL: " + sql, e);
		} finally {
			closeResultSetStatement(resultSet, sql);
		}

		return count;
	}

	/**
	 * Execute a deletion
	 * 
	 * @param connection
	 * @param table
	 * @param where
	 * @param args
	 * @return
	 */
	public static int delete(Connection connection, String table, String where,
			String[] args) {
		StringBuilder delete = new StringBuilder();
		delete.append("delete from ").append(table);
		if (where != null) {
			delete.append(" where ").append(where);
		}
		String sql = delete.toString();

		PreparedStatement statement = null;

		int count = 0;
		try {
			statement = connection.prepareStatement(sql);
			setArguments(statement, args);
			count = statement.executeUpdate();
		} catch (SQLException e) {
			throw new GeoPackageException(
					"Failed to execute SQL delete statement: " + sql, e);
		} finally {
			closeStatement(statement, sql);
		}

		return count;
	}

	/**
	 * Update table rows
	 * 
	 * @param connection
	 * @param table
	 * @param values
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */
	public static int update(Connection connection, String table,
			ContentValues values, String whereClause, String[] whereArgs) {

		StringBuilder update = new StringBuilder();
		update.append("update ").append(table).append(" set ");

		int setValuesSize = values.size();
		int argsSize = (whereArgs == null) ? setValuesSize
				: (setValuesSize + whereArgs.length);
		Object[] args = new Object[argsSize];
		int i = 0;
		for (String colName : values.keySet()) {
			update.append((i > 0) ? "," : "");
			update.append(colName);
			args[i++] = values.get(colName);
			update.append("=?");
		}
		if (whereArgs != null) {
			for (i = setValuesSize; i < argsSize; i++) {
				args[i] = whereArgs[i - setValuesSize];
			}
		}
		if (whereClause != null) {
			update.append(" WHERE ");
			update.append(whereClause);
		}
		String sql = update.toString();

		PreparedStatement statement = null;

		int count = 0;
		try {
			statement = connection.prepareStatement(sql);
			setArguments(statement, args);
			count = statement.executeUpdate();
		} catch (SQLException e) {
			throw new GeoPackageException(
					"Failed to execute SQL update statement: " + sql, e);
		} finally {
			closeStatement(statement, sql);
		}

		return count;
	}

	/**
	 * Insert a new row
	 * 
	 * @param connection
	 * @param table
	 * @param values
	 * @return row id or -1 on an exception
	 */
	public static long insert(Connection connection, String table,
			ContentValues values) {
		try {
			return insertOrThrow(connection, table, values);
		} catch (Exception e) {
			log.log(Level.WARNING, "Error inserting into table: " + table
					+ ", Values: " + values, e);
			return -1;
		}
	}

	/**
	 * Insert a new row
	 * 
	 * @param connection
	 * @param table
	 * @param values
	 * @return row id
	 */
	public static long insertOrThrow(Connection connection, String table,
			ContentValues values) {

		StringBuilder insert = new StringBuilder();
		insert.append("insert into ").append(table).append("(");

		Object[] args = null;
		int size = (values != null && values.size() > 0) ? values.size() : 0;

		args = new Object[size];
		int i = 0;
		for (String colName : values.keySet()) {
			insert.append((i > 0) ? "," : "");
			insert.append(colName);
			args[i++] = values.get(colName);
		}
		insert.append(')');
		insert.append(" values (");
		for (i = 0; i < size; i++) {
			insert.append((i > 0) ? ",?" : "?");
		}
		insert.append(')');

		String sql = insert.toString();

		PreparedStatement statement = null;

		long id = 0;
		try {
			statement = connection.prepareStatement(sql);
			setArguments(statement, args);
			int count = statement.executeUpdate();

			if (count == 0) {
				throw new GeoPackageException(
						"Failed to execute SQL insert statement: " + sql
								+ ". No rows added from execution.");
			}

			try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
				if (generatedKeys.next()) {
					id = generatedKeys.getLong(1);
				} else {
					throw new GeoPackageException(
							"Failed to execute SQL insert statement: " + sql
									+ ". No row id was found.");
				}
			}
		} catch (SQLException e) {
			throw new GeoPackageException(
					"Failed to execute SQL insert statement: " + sql, e);
		} finally {
			closeStatement(statement, sql);
		}

		return id;
	}

	/**
	 * Set the prepared statement arguments
	 * 
	 * @param statement
	 * @param selectionArgs
	 * @throws SQLException
	 */
	public static void setArguments(PreparedStatement statement,
			Object[] selectionArgs) throws SQLException {
		if (selectionArgs != null) {
			for (int i = 0; i < selectionArgs.length; i++) {
				statement.setObject(i + 1, selectionArgs[i]);
			}
		}
	}

	/**
	 * Close the statement
	 * 
	 * @param statement
	 * @param sql
	 */
	public static void closeStatement(Statement statement, String sql) {
		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException e) {
				log.log(Level.WARNING, "Failed to close SQL Statement: " + sql,
						e);
			}
		}
	}

	/**
	 * Close the ResultSet
	 * 
	 * @param resultSet
	 * @param sql
	 */
	public static void closeResultSet(ResultSet resultSet, String sql) {
		if (resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException e) {
				log.log(Level.WARNING, "Failed to close SQL ResultSet: " + sql,
						e);
			}
		}
	}

	/**
	 * Close the ResultSet Statement from which it was created, which closes all
	 * ResultSets as well
	 * 
	 * @param resultSet
	 * @param sql
	 */
	public static void closeResultSetStatement(ResultSet resultSet, String sql) {
		if (resultSet != null) {
			try {
				resultSet.getStatement().close();
			} catch (SQLException e) {
				log.log(Level.WARNING, "Failed to close SQL ResultSet: " + sql,
						e);
			}
		}
	}

}
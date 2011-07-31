package org.oobium.persist.db.mysql;

import static org.oobium.utils.coercion.TypeCoercer.coerce;
import static org.oobium.utils.StringUtils.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.sql.ConnectionPoolDataSource;

import org.oobium.persist.db.Database;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

public class MySqlDatabase extends Database {

	public MySqlDatabase(String client, Map<String, Object> properties) {
		super(client, properties);
	}

	@Override
	protected Map<String, Object> initProperties(Map<String, Object> properties) {
		Map<String, Object> props = new HashMap<String, Object>(properties);
		if(blank(props.get("database"))) {
			throw new IllegalArgumentException("\"database\" field cannot be blank in persist configuration");
		}
		if(props.get("host") == null) {
			props.put("host", "127.0.0.1");
		}
		if(props.get("port") == null) {
			props.put("port", 3306);
		}
		if(props.get("username") == null) {
			props.put("username", "root");
		}
		if(props.get("password") == null) {
			props.put("password", "");
		}
		return props;
	}

	@Override
	protected void createDatabase() throws SQLException {
		exec("CREATE DATABASE " + properties.get("database"));
	}
	
	private void exec(String cmd) throws SQLException {
		Connection connection = null;
		Statement statement = null;
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();

			StringBuilder sb = new StringBuilder();
			sb.append("jdbc:mysql://").append(properties.get("host")).append(':').append(properties.get("port"));

			String dbURL = sb.toString();
			if(logger.isLoggingDebug()) {
				logger.debug(cmd + ": " + dbURL);
			}
			
	    	connection = DriverManager.getConnection(dbURL, (String) properties.get("username"), (String) properties.get("password"));
			statement = connection.createStatement();
			statement.execute(cmd);
		} catch(SQLException e) {
			throw e;
		} catch(Exception e) {
			throw new SQLException("could not create database", e);
		} finally {
			if(statement != null) {
				try {
					statement.close();
				} catch(SQLException e) {
					// discard
				}
			}
			if(connection != null) {
				try {
					connection.close();
				} catch(SQLException e) {
					// discard
				}
			}
		}
	}
	
	@Override
	protected ConnectionPoolDataSource createDataSource() {
		MysqlConnectionPoolDataSource ds = new MysqlConnectionPoolDataSource();
		ds.setDatabaseName(coerce(properties.get("database"), String.class));
		ds.setServerName(coerce(properties.get("host"), String.class));
		ds.setPortNumber(coerce(properties.get("port"), int.class));
		ds.setUser(coerce(properties.get("username"), String.class));
		ds.setPassword(coerce(properties.get("password"), String.class));
		return ds;
	}

	@Override
	protected void dropDatabase() throws SQLException {
		exec("DROP DATABASE " + properties.get("database"));
	}

	@Override
	protected String getDatabaseIdentifier() {
		return ((MysqlConnectionPoolDataSource) getDataSource()).getDatabaseName();
	}

}

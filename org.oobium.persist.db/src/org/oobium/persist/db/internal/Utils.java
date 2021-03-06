/*******************************************************************************
 * Copyright (c) 2010 Oobium, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Jeremy Dowdall <jeremy@oobium.com> - initial API and implementation
 ******************************************************************************/
package org.oobium.persist.db.internal;

//import static org.oobium.persist.SessionCache.getCacheById;
//import static org.oobium.persist.SessionCache.setCache;
import static org.oobium.utils.StringUtils.blank;
import static org.oobium.utils.StringUtils.columnName;
import static org.oobium.utils.StringUtils.tableName;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.oobium.logging.LogProvider;
import org.oobium.logging.Logger;
import org.oobium.persist.Model;
import org.oobium.persist.ModelAdapter;
import org.oobium.persist.ModelDescription;
import org.oobium.persist.db.DbPersistService;
import org.oobium.utils.SqlUtils;

public class Utils {

	private static final Logger logger = LogProvider.getLogger(DbPersistService.class);
	
	public static final String ID = columnName(ModelDescription.ID);
	public static final String CREATED_AT = columnName(ModelDescription.CREATED_AT);
	public static final String UPDATED_AT = columnName(ModelDescription.UPDATED_AT);
	public static final String CREATED_ON = columnName(ModelDescription.CREATED_ON);
	public static final String UPDATED_ON = columnName(ModelDescription.UPDATED_ON);

	public static final String SELECT = "select ";
	public static final String FROM = "from ";
	public static final String WHERE = "where ";
	public static final String ORDER_BY = "order by ";
	public static final String LIMIT = "limit ";
	public static final String INCLUDE = "include:";
	
	public static final Pattern valuePattern = Pattern.compile("#\\{(\\d+)\\}");

	private static final char[][] sqlStarts = new char[][] { "where".toCharArray(), "order by".toCharArray(), "limit".toCharArray(), "include".toCharArray() };
	
	
	private static String asString(Object object) {
		if(object instanceof Model) {
			return ((Model) object).asSimpleString();
		}
		return String.valueOf(object);
	}

	public static int getDbType(Connection connection) {
		String name = connection.getClass().getCanonicalName();
		if(name.contains(".mysql.")) {
			return SqlUtils.MYSQL;
		}
		if(name.contains(".postgresql.")) {
			return SqlUtils.POSTGRESQL;
		}
		if(name.contains(".derby.")) {
			return SqlUtils.DERBY;
		}
		if(connection.toString().contains(".postgresql.")) { // it's a proxy wrapping a PGConnection
			return SqlUtils.POSTGRESQL;
		}
		throw new IllegalArgumentException("unknown database type: " + name);
	}
	
	public static Object getObject(Class<? extends Model> clazz, int id) throws InstantiationException, IllegalAccessException, NoSuchFieldException {
//		Model model = getCacheById(clazz, id);
//		if(model == null) {
//			model = clazz.newInstance();
//			model.setId(id);
//		}
//		return model;
		Model model = clazz.newInstance();
		model.setId(id);
		return model;
	}
	
	public static String getWhere(String sql) throws SQLException {
		if(blank(sql)) {
			return null;
		}
		int ix = sql.indexOf(WHERE);
		if(ix == -1) {
			return null;
		} else {
			ix += WHERE.length();
		}
		int[] ixs = new int[] { sql.indexOf(ORDER_BY), sql.indexOf(LIMIT), sql.indexOf(INCLUDE) };
		int stop = sql.length();
		for(int i : ixs) {
			if(i != -1) {
				stop = Math.min(stop, i);
			}
		}
		if(stop > ix) {
			return sql.substring(ix, stop);
		} else {
			throw new SQLException("invalid sql format: " + sql);
		}
	}

	public static boolean isMapQuery(String sql) {
		if(sql == null || sql.length() == 0) {
			return false;
		}
		for(char[] test : sqlStarts) {
			if(isNextIgnoreCase(sql, 0, test)) {
				return false;
			}
		}
		return true;
	}
	
	private static boolean isNextIgnoreCase(String s, int start, char...test) {
		if(start < 0 || start >= s.length()) {
			return false;
		}
		if(test.length == 0 || (s.length() - start) < test.length) {
			return false;
		}
		for(int i = 0; i < test.length; i++) {
			if(Character.toLowerCase(s.charAt(start+i)) != Character.toLowerCase(test[i])) {
				return false;
			}
		}
		return true;
	}

	public static StringBuilder objectQuery(Class<?>[] classes, boolean addWhere) {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT * FROM ").append(tableName(classes[0]));
		for(int i = 1; i < classes.length; i++) {
			sb.append(',').append(tableName(classes[i]));
		}
		if(addWhere || classes.length > 1) {
			sb.append(" WHERE ");
			if(classes.length > 1) {
				for(int i = classes.length - 2; i >= 0; i--) {
					if(i != classes.length - 2) {
						sb.append(" AND ");
					}
					sb.append(tableName(classes[i])).append(".id=").append(tableName(classes[i + 1])).append(".super_id");
				}
				if(addWhere) {
					sb.append(" AND ");
				}
			}
		}
		return sb;
	}

	public static String objectQuery(Class<?>[] classes, String sql) {
		if(sql == null || sql.isEmpty()) {
			return objectQuery(classes, false).toString();
		} else if(sql.startsWith("WHERE ") || sql.startsWith("where ") || sql.startsWith("ORDER BY ") || sql.startsWith("order by ")) {
			StringBuilder sb = objectQuery(classes, false);
			sb.append(' ').append(sql);
			return sb.toString();
		} else {
			return sql;
		}
	}
	
	public static void setFields(Model model, Map<String, Object> data, String...fields) {
		if(logger.isLoggingTrace()) {
			logger.trace("start setFields " + model.asSimpleString());
		}
		if(fields.length == 0) {
			ModelAdapter adapter = ModelAdapter.getAdapter(model.getClass());
			for(Entry<String, Object> entry : data.entrySet()) {
				String field = entry.getKey();
				if(!ModelDescription.ID.equals(field)) {
					Object value = entry.getValue();
					if(logger.isLoggingTrace()) {
						logger.trace("  " + field + " <- " + asString(value));
					}
					try {
						if(adapter.hasOne(field) && value instanceof Integer) {
							value = getObject(adapter.getHasOneClass(field), (Integer) value);
						}
						model.put(field, value);
					} catch(NoSuchFieldException e) {
						if(logger.isLoggingTrace()) {
							logger.trace("database field " + field + " does not exist in " + model.getClass());
						}
					} catch(Exception e) {
						if(logger.isLoggingTrace()) {
							logger.trace("error setting field " + field + " in " + model.getClass(), e);
						}
					}
				}
			}
		} else {
			for(String field : fields) {
				Object value = data.get(field);
				if(logger.isLoggingTrace()) {
					logger.trace("  " + field + " <- " + asString(value));
				}
				model.put(field, value);
			}
		}
		logger.trace("end setFields");
	}

}

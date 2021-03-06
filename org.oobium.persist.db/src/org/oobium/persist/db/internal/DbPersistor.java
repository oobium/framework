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

import static org.oobium.persist.db.internal.Utils.CREATED_AT;
import static org.oobium.persist.db.internal.Utils.CREATED_ON;
import static org.oobium.persist.db.internal.Utils.ID;
import static org.oobium.persist.db.internal.Utils.UPDATED_AT;
import static org.oobium.persist.db.internal.Utils.UPDATED_ON;
import static org.oobium.persist.db.internal.Utils.getDbType;
import static org.oobium.persist.db.internal.Utils.getWhere;
import static org.oobium.persist.db.internal.Utils.setFields;
import static org.oobium.utils.SqlUtils.asFieldMaps;
import static org.oobium.utils.SqlUtils.asLists;
import static org.oobium.utils.SqlUtils.getSqlType;
import static org.oobium.utils.SqlUtils.isInsert;
import static org.oobium.utils.SqlUtils.limit;
import static org.oobium.utils.SqlUtils.safeSqlWord;
import static org.oobium.utils.SqlUtils.setObject;
import static org.oobium.utils.StringUtils.blank;
import static org.oobium.utils.StringUtils.columnName;
import static org.oobium.utils.StringUtils.joinColumn;
import static org.oobium.utils.StringUtils.joinColumns;
import static org.oobium.utils.StringUtils.joinTable;
import static org.oobium.utils.StringUtils.tableName;
import static org.oobium.utils.json.JsonUtils.toList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.oobium.logging.LogProvider;
import org.oobium.logging.Logger;
import org.oobium.persist.Model;
import org.oobium.persist.ModelAdapter;
import org.oobium.persist.PersistService;
import org.oobium.persist.Relation;
import org.oobium.persist.db.DbPersistService;
import org.oobium.utils.SqlUtils;
import org.oobium.utils.StringUtils;

public class DbPersistor {

	private static final Cell createdAt = new Cell(CREATED_AT, Types.BIGINT, null);
	private static final Cell updatedAt = new Cell(UPDATED_AT, Types.BIGINT, null);
	private static final Cell createdOn = new Cell(CREATED_ON, Types.DATE, "CURRENT_DATE");
	private static final Cell updatedOn = new Cell(UPDATED_ON, Types.DATE, "CURRENT_DATE");

	private static final Logger logger = LogProvider.getLogger(DbPersistService.class);

	private static String join(String starter, Object[] segments, String closer, String separator) {
		Object[] oa = new Object[segments.length];
		for(int i = 0; i < segments.length; i++) {
			if(segments[i] instanceof Model) {
				oa[i] = ((Model) segments[i]).asSimpleString();
			} else {
				oa[i] = segments[i];
			}
		}
		return StringUtils.join(starter, oa, closer, separator);
	}
	

	private final Connection connection;
	private final int dbType;
	
	public DbPersistor(Connection connection) {
		this(connection, getDbType(connection));
	}
	
	public DbPersistor(Connection connection, int dbType) {
		this.connection = connection;
		this.dbType = dbType;
	}

	private void addDeferredToCreate(Model model, Map<Model, List<String>> map) throws NoSuchFieldException {
		ModelAdapter adapter = ModelAdapter.getAdapter(model.getClass());

		for(String field : adapter.getHasManyFields()) {
			if(model.isSet(field) && !adapter.isManyToOne(field)) {
				if(!map.containsKey(model)) {
					map.put(model, new ArrayList<String>());
				}
				map.get(model).add(field);
			}
		}

	}

	private void addDeferredToUpdate(Model model, Map<Model, List<String>> map) throws NoSuchFieldException {
		ModelAdapter adapter = ModelAdapter.getAdapter(model.getClass());

		for(String field : adapter.getHasOneFields()) {
			if(adapter.isOneToOne(field) && !adapter.hasKey(field)) {
				if(!map.containsKey(model)) {
					map.put(model, new ArrayList<String>());
				}
				map.get(model).add(field);
			}
		}
	}

	private void addModelsToCreate(int pos, Model model, List<Model> models) throws NoSuchFieldException {
		models.add(pos, model);

		ModelAdapter adapter = ModelAdapter.getAdapter(model);

		for(String field : adapter.getHasOneFields()) {
			Model one = (Model) (model.isSet(field) ? model.get(field) : null);
			if(one != null && one.isNew() && !models.contains(one)) {
				if(adapter.isOneToOne(field) && !adapter.hasKey(field)) {
					addModelsToCreate(pos, one, models); // one needs to be created first, or else there's a constraint violation
				} else {
					addModelsToCreate(models.size(), one, models);
				}
			}
		}
		for(String field : adapter.getHasManyFields()) {
			if(adapter.isManyToOne(field) && !adapter.isEmbedded(field)) {
				Collection<?> collection = (Collection<?>) model.get(field);
				if(!collection.isEmpty()) {
					for(Object object : collection) {
						Model many = (Model) object;
						if(many.isNew() && !models.contains(many)) {
							addModelsToCreate(0, many, models);
						}
					}
				}
			}
		}
	}

	public int count(Class<? extends Model> clazz, String sql, Object... values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start count: " + clazz.getCanonicalName() + join(", " + sql + " <- [", values, "]", ", "));
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT COUNT(*) FROM ").append(tableName(clazz));
		if(!blank(sql)) {
			String where = getWhere(sql);
			if(!blank(where)) {
				sb.append(" WHERE ").append(where);
			}
		}

		String query = sb.toString();
		logger.trace(query);
		
		Statement s = null;
		ResultSet rs = null;
		try {
			if(values.length == 0) {
				s = connection.createStatement();
				rs = s.executeQuery(query);
			} else {
				PreparedStatement ps = connection.prepareStatement(query);
				setStatementValues(ps, values);
				rs = ps.executeQuery();
			}
			if(rs.next()) {
				return rs.getInt(1);
			}
		} finally {
			logger.debug("end count");
			if(s != null) {
				s.close();
			}
		}

		return -1;
	}

	public void create(Model[] models) throws SQLException, NoSuchFieldException {
		for(Model model : models) {
			doCreate(model);
		}
	}

	private void handleDependentDelete(ModelAdapter adapter, Model model, String field) throws SQLException {
		Object o = model.get(field, false);
		if(o instanceof Model) {
			destroy(new Model[] { (Model) o  });
		} else if(o instanceof Collection){
			Model[] models = ((Collection<?>) o).toArray(new Model[0]);
			destroy(models);
		}
	}

	private void handleDependentNullify(ModelAdapter adapter, Model model, String field) throws SQLException {
		String table = tableName(adapter.getOppositeType(field));
		String column = safeSqlWord(dbType, columnName(adapter.getOpposite(field)));

		exec("UPDATE " + table + " SET " + column + "=null WHERE " + column + "=" + model.getId());
	}
	
	private void handleDependentNullify(ModelAdapter adapter, Model model, String field, List<String> linkbacks) throws SQLException {
		String table = tableName(adapter.getOppositeType(field));

		for(String linkback : linkbacks) {
			String column = safeSqlWord(dbType, columnName(linkback));
			exec("UPDATE " + table + " SET " + column + "=null WHERE " + column + "=" + model.getId());
		}
	}
	
	private void handleDependents(Model model, boolean beforeDestroy) throws SQLException {
		ModelAdapter adapter = ModelAdapter.getAdapter(model);
		if(beforeDestroy) {
			for(String field : adapter.getRelationFields()) {
				Relation relation = adapter.getRelation(field);
				switch(relation.dependent()) {
				case Relation.DELETE:
					if(adapter.hasMany(field) && adapter.isManyToOne(field)) {
						handleDependentDelete(adapter, model, field);
					} else {
						model.get(field); // make sure all DELETE and NULLIFY fields are loaded
						if(adapter.hasOne(field)) {
							if(adapter.isOneToOne(field)) {
								if(!adapter.hasKey(field)) {
									handleDependentDelete(adapter, model, field);
								}
							} else {
								List<String> linkbacks = adapter.getRelationLinkBacks(field);
								if(!linkbacks.isEmpty()) {
									handleDependentNullify(adapter, model, field, linkbacks);
								}
							}
						}
					}
					break;
				case Relation.NULLIFY:
					if(adapter.hasMany(field) && !adapter.isManyToMany(field)) {
						handleDependentNullify(adapter, model, field);
					} else {
						model.get(field); // make sure all DELETE and NULLIFY fields are loaded
					}
					break;
				}
			}
		} else {
			for(String field : adapter.getHasOneFields()) {
				Relation relation = adapter.getRelation(field);
				switch(relation.dependent()) {
				case Relation.DELETE:
					if(!adapter.isOneToOne(field) || adapter.hasKey(field)) {
						handleDependentDelete(adapter, model, field);
					}
					break;
				case Relation.NULLIFY:
					handleDependentNullify(adapter, model, field);
					break;
				}
			}
			for(String field : adapter.getHasManyFields()) {
				Relation relation = adapter.getRelation(field);
				switch(relation.dependent()) {
				case Relation.DELETE:
					if(!adapter.isManyToOne(field)) {
						handleDependentDelete(adapter, model, field);
					}
					break;
				case Relation.NULLIFY:
					handleDependentNullify(adapter, model, field);
					break;
				}
			}
		}
	}
	
	public void destroy(Model[] models) throws SQLException {
		for(Model model : models) {
			handleDependents(model, true);
			doDestroy(model);
			handleDependents(model, false);
		}
	}

	private int doCreate(Model model) throws SQLException, NoSuchFieldException {
		logger.debug("start doCreate {}", model.asSimpleString());

		if(!model.isNew()) {
			throw new SQLException("model has already been created");
		}

		List<Model> models = new ArrayList<Model>();
		addModelsToCreate(0, model, models);

		Map<Model, List<String>> deferredCreate = new HashMap<Model, List<String>>();
		for(Model deferred : models) {
			addDeferredToCreate(deferred, deferredCreate);
		}
		
		Map<Model, List<String>> deferredUpdate = new HashMap<Model, List<String>>();
		for(Model deferred : models) {
			addDeferredToUpdate(deferred, deferredUpdate);
		}

		for(int i = models.size() - 1; i >= 0; i--) {
			Model next = models.get(i);
			PersistService p1 = model.getPersistor();
			PersistService p2 = next.getPersistor();
			if(p1 == p2) {
				doCreateModel(next);
			} else {
				try {
					p2.create(next);
				} catch(Exception e) {
					throw new SQLException(e);
				}
			}
		}

		for(Entry<Model, List<String>> entry : deferredCreate.entrySet()) {
			doCreateDeferred(entry.getKey(), entry.getValue());
		}

		for(Entry<Model, List<String>> entry : deferredUpdate.entrySet()) {
			doUpdateDeferred(entry.getKey(), entry.getValue());
		}
		
		logger.debug("end doCreate");
		return model.getId(int.class);
	}

	private int doCreate(String table, List<Cell> cells) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("INSERT INTO ").append(table).append('(');
		for(Iterator<Cell> iter = cells.iterator(); iter.hasNext();) {
			sb.append(safeSqlWord(dbType, iter.next().column));
			if(iter.hasNext()) {
				sb.append(",");
			}
		}
		sb.append(") VALUES(");
		for(Iterator<Cell> iter = cells.iterator(); iter.hasNext();) {
			Cell cell = iter.next();
			if(cell.isQuery) {
				sb.append(cell.query());
				iter.remove();
			} else if(isDateTimeField(cell) && cell.value instanceof Long) {
				iter.remove();
				sb.append(cell.value);
			} else {
				sb.append("?");
			}
			if(iter.hasNext()) {
				sb.append(",");
			}
		}
		sb.append(')');

		String sql = sb.toString();
		logger.trace(sql);

		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for(int i = 0; i < cells.size(); i++) {
				Cell cell = cells.get(i);
				logger.trace("  {} -> {}", safeSqlWord(dbType, cell.column), cell.value);
				setObject(ps, i + 1, cell.value, cell.type);
			}
			ps.executeUpdate();

			rs = ps.getGeneratedKeys();
			int id = (rs.next()) ? rs.getInt(1) : -1;
			logger.trace("  {} <- {}", ID, id);
			return id;
		} finally {
			if(ps != null) {
				ps.close();
			}
			if(rs != null) {
				rs.close();
			}
		}
	}
	
	private void doCreateDeferred(Model model, List<String> deferredMany) throws NoSuchFieldException, SQLException {
		ModelAdapter adapter = ModelAdapter.getAdapter(model.getClass());
		
		for(String field : deferredMany) {
			if(model.isSet(field)) {
				Collection<?> collection = (Collection<?>) model.get(field);
				if(!collection.isEmpty()) {
					List<Integer> dIds = new ArrayList<Integer>();
					for(Object object : collection) {
						Model dModel = (Model) object;
						int dId = dModel.isNew() ? doCreate(dModel) : dModel.getId(int.class);
						dIds.add(dId);
					}
					if(adapter.isThrough(field)) {
						throw new SQLException("creating from a through field not yet supported");
					} else {
						String table1 = tableName(adapter.getModelClass());
						String column1 = columnName(field);
						String table2 = tableName(adapter.getHasManyMemberClass(field));
						String column2 = columnName(adapter.getOpposite(field));
						String table = joinTable(table1, column1, table2, column2);
						String[] columns = joinColumns(table1, column1, table2, column2);
						doUpdateManyToMany(table, columns[0], model.getId(int.class), columns[1], dIds);
					}
				}
			}
		}
	}

	private void doUpdateDeferred(Model model, List<String> deferredOne) throws SQLException {
		ModelAdapter adapter = ModelAdapter.getAdapter(model);
		
		for(String field : deferredOne) {
			if(model.isSet(field)) {
				Model opposite = (Model) model.get(field);
				if(opposite != null) {
					doUpdateOneToOne(adapter, model, field, opposite.getId());
				}
			}
		}
	}

	private Cell createCell(Model model, String field) {
		Model value = (Model) (model.isSet(field) ? model.get(field) : null);
		return new Cell(columnName(field), Types.INTEGER, (value != null) ? value.getId() : null);
	}
	
	private void doCreateModel(Model model) throws SQLException, NoSuchFieldException {
		if(model.isNew()) {
			ModelAdapter adapter = ModelAdapter.getAdapter(model);
			String table = tableName(adapter.getModelClass());

			boolean needsCreatedAt, needsCreatedOn, needsUpdatedAt, needsUpdatedOn;
			needsCreatedAt = needsUpdatedAt = adapter.isTimeStamped();
			needsCreatedOn = needsUpdatedOn = adapter.isDateStamped();
			
			List<Cell> cells = new ArrayList<Cell>();
			for(String field : adapter.getAttributeFields()) {
				if(model.isSet(field) && !adapter.isVirtual(field)) {
					String name = columnName(field);
					if(needsCreatedAt && name.equals(createdAt.column)) needsCreatedAt = false;
					if(needsCreatedOn && name.equals(createdOn.column)) needsCreatedOn = false;
					if(needsUpdatedAt && name.equals(updatedAt.column)) needsUpdatedAt = false;
					if(needsUpdatedOn && name.equals(updatedOn.column)) needsUpdatedOn = false;
					int type = getSqlType(adapter.getClass(field));
					Object val = model.get(field);
					cells.add(new Cell(name, type, val));
				}
			}
			
			for(String field : adapter.getHasOneFields()) {
				if(adapter.isOneToOne(field)) {
					if(adapter.hasKey(field)) {
						Cell cell = createCell(model, field);
						if(cell.value != null) {
							String column = safeSqlWord(dbType, cell.column);
							exec("UPDATE " + table + " SET " + column + "=null WHERE " + column + "=" + cell.value);
						}
						cells.add(cell);
					} // else, skip it
				}
				else if(!adapter.isThrough(field)/* && (!adapter.isOneToOne(field) || adapter.hasKey(field))*/) {
					cells.add(createCell(model, field));
				}
			}
			
			if(needsCreatedAt || needsCreatedOn || needsUpdatedAt || needsUpdatedOn) {
				long now = System.currentTimeMillis();
				if(needsCreatedAt) cells.add(createdAt.withValue(now));
				if(needsCreatedOn) cells.add(createdOn.withValue(now));
				if(needsUpdatedAt) cells.add(updatedAt.withValue(now));
				if(needsUpdatedOn) cells.add(updatedOn.withValue(now));
			}
			
			if(cells.isEmpty()) {
				throw new SQLException("can not create an empty model: " + model);
			}
			
			int id = doCreate(table, cells);
			model.setId(id);

			if(model.isNew()) {
				throw new SQLException("could not create record for " + model);
			}
		}
	}
	
	private int exec(String sql) throws SQLException {
		logger.trace(sql);
		Statement s = null;
		try {
			s = connection.createStatement();
			return s.executeUpdate(sql);
		} finally {
			if(s != null) {
				try {
					s.close();
				} catch(SQLException e) {
					// discard
				}
			}
		}
	}
	
	private void doDestroy(Model model) throws SQLException {
		logger.debug("start doDestroy {}", model.asSimpleString());

		ModelAdapter adapter = ModelAdapter.getAdapter(model);
		
		for(String field : adapter.getHasManyFields()) {
			if(adapter.hasMany(field) && !adapter.isManyToOne(field) && !adapter.isThrough(field)) {
				String table1 = tableName(adapter.getModelClass());
				String column1 = columnName(field);
				String table2 = tableName(adapter.getHasManyMemberClass(field));
				String column2 = columnName(adapter.getOpposite(field));
				
				String table = tableName(table1, column1, table2, column2);
				String column = joinColumn(table1, column1, table2, column2);

				exec("DELETE FROM " + table + " WHERE " + safeSqlWord(dbType, column) + "=" + model.getId());
			}
		}

		exec("DELETE FROM " + tableName(adapter.getModelClass()) + " WHERE id=" + model.getId());

		logger.debug("end doDestroy");
	}

	private Statement doExecuteQuery(String sql, Object...values) throws SQLException {
		int limit = -1;
		int ix = sql.toLowerCase().indexOf(" limit ");
		if(ix != -1) {
			try {
				limit = Integer.parseInt(sql.substring(ix + 7).trim());
				sql = sql.substring(0, ix);
			} catch(NumberFormatException nfe) {
				throw new SQLException(nfe.getMessage(), nfe);
			}
		}

		if(values.length == 0) {
			Statement s = connection.createStatement();
			if(limit > 0) {
				s.setMaxRows(limit);
			}
			s.executeQuery(sql);
			return s;
		} else {
			PreparedStatement ps = connection.prepareStatement(sql);
			if(limit > 0) {
				ps.setMaxRows(limit);
			}
			setStatementValues(ps, values);
			ps.executeQuery();
			return ps;
		}
	}

	/**
	 * @throws SQLException if record does not exist in the database (its relations may have already been saved though)
	 */
	private void doUpdate(Model model) throws SQLException, NoSuchFieldException {
		logger.debug("start doUpdate: {}", model.asSimpleString());

		int id = model.getId(int.class);
		if(id < 1) {
			throw new SQLException(ID + " has not yet been set for " + model.asSimpleString());
		}

		if(!model.isEmpty()) {
			ModelAdapter adapter = ModelAdapter.getAdapter(model.getClass());
			
			boolean needsUpdatedAt, needsUpdatedOn;
			needsUpdatedAt = adapter.isTimeStamped();
			needsUpdatedOn = adapter.isDateStamped();
			
			Class<? extends Model> clazz = model.getClass();
			List<Cell> cells = new ArrayList<Cell>();
			
			for(String field : adapter.getFields()) {
				if(model.isSet(field) && !adapter.isVirtual(field) && !adapter.isThrough(field)) {
					if(adapter.hasOne(field)) {
						Model fModel = (Model) model.get(field);
						Integer fId = (fModel != null) ? fModel.getId(Integer.class) : null;
						if(fId != null && fId < 1) {
							fId = doCreate(fModel);
						}
						if(adapter.isOneToOne(field)) {
							if(adapter.hasKey(field)) {
								if(fId != null) {
									clearOneToOne(adapter, field, fId);
								}
								cells.add(new Cell(columnName(field), Types.INTEGER, fId));
							} else {
								doUpdateOneToOne(adapter, model, field, fId);
							}
						} else {
							cells.add(new Cell(columnName(field), Types.INTEGER, fId));
						}
					} else if(adapter.hasMany(field)) {
						Collection<?> collection = (Collection<?>) model.get(field);
						if(adapter.isManyToOne(field)) {
							Class<? extends Model> type = adapter.getHasManyMemberClass(field);
							PersistService p = adapter.getOppositePersistService(field);
							if(p == null) {
								String table = tableName(type);
								String column = columnName(adapter.getOpposite(field));
								doUpdateManyToOne(table, column, id, collection);
							} else {
								// what now? TODO mixed persist services
							}
						} else {
							List<Integer> dIds = new ArrayList<Integer>();
							for(Object object : collection) {
								Model dModel = (Model) object;
								int dId = dModel.isNew() ? doCreate(dModel) : dModel.getId(int.class);
								dIds.add(dId);
							}
							String table1 = tableName(adapter.getModelClass());
							String column1 = columnName(field);
							String table2 = tableName(adapter.getHasManyMemberClass(field));
							String column2 = columnName(adapter.getOpposite(field));
							String table = joinTable(table1, column1, table2, column2);
							String[] columns = joinColumns(table1, column1, table2, column2);
							doUpdateManyToMany(table, columns[0], model.getId(int.class), columns[1], dIds);
						}
					} else if(adapter.hasAttribute(field)) {
						String name = columnName(field);
						if(!name.equals(createdAt.column) && !name.equals(createdOn.column)) {
							if(needsUpdatedAt && name.equals(updatedAt.column)) needsUpdatedAt = false;
							if(needsUpdatedOn && name.equals(updatedOn.column)) needsUpdatedOn = false;
							int type = getSqlType(adapter.getClass(field));
							Object val = model.get(field);
							cells.add(new Cell(name, type, val));
						}
					}
				}
			}
			
			if(needsUpdatedAt || needsUpdatedOn) {
				long now = System.currentTimeMillis();
				if(needsUpdatedAt) cells.add(updatedAt.withValue(now));
				if(needsUpdatedOn) cells.add(updatedOn.withValue(now));
			}
			
			if(!cells.isEmpty()) {
				int result = doUpdate(tableName(clazz), model.getId(int.class), cells);
				if(result < 1) {
					throw new SQLException("could not update " + model.asSimpleString() + " (does not exist in database)");
				}
			}
			
		}			
		logger.debug("end doUpdate");
	}

	private int doUpdate(String table, int id, List<Cell> cells) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ").append(table).append(" SET ");
		for(Iterator<Cell> iter = cells.iterator(); iter.hasNext();) {
			Cell cell = iter.next();
			if(isUpdatedDateTimeField(cell) && cell.value instanceof Long) {
				iter.remove();
				sb.append(cell.column).append('=').append(cell.value);
			} else {
				sb.append(safeSqlWord(dbType, cell.column)).append("=?");
			}
			if(iter.hasNext()) {
				sb.append(",");
			}
		}
		sb.append(" WHERE id=").append(id);

		String sql = sb.toString();
		logger.trace(sql);

		PreparedStatement ps = null;
		try {
			ps = connection.prepareStatement(sql);
			for(int i = 0; i < cells.size(); i++) {
				Cell cell = cells.get(i);
				setObject(ps, i + 1, cell.value, cell.type);
				if(logger.isLoggingTrace()) {
					logger.trace("  " + safeSqlWord(dbType, cell.column) + " -> " + cell.value);
				}
			}
			return ps.executeUpdate();
		} finally {
			if(ps != null) {
				ps.close();
			}
		}
	}

	/**
	 * Update a Many To Many collection
	 * 
	 * @param connection
	 * @param table
	 * @param column1
	 * @param id1
	 * @param column2
	 * @param id2s
	 * @throws SQLException
	 */
	private void doUpdateManyToMany(String table, String column1, int id1, String column2, List<Integer> id2s) throws SQLException {
		Statement s = null;
		try {
			s = connection.createStatement();

			String sql = "DELETE FROM " + table + " WHERE " + column2 + "=" + id1;
			logger.trace(sql);
			s.executeUpdate(sql);

			if(!id2s.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("INSERT INTO ").append(table).append('(').append(column1).append(',').append(column2).append(") VALUES");
				for(Iterator<Integer> iter = id2s.iterator(); iter.hasNext();) {
					sb.append('(').append(iter.next()).append(',').append(id1).append(')');
					if(iter.hasNext()) {
						sb.append(',');
					}
				}

				sql = sb.toString();
				logger.trace(sql);

				s.executeUpdate(sql);
			}
		} finally {
			s.close();
		}
	}

	private void doUpdateOneToOne(ModelAdapter adapter, Model model, String field, Object id) throws SQLException {
		String table = tableName(adapter.getOppositeType(field));
		String column = safeSqlWord(dbType, columnName(adapter.getOpposite(field)));
		
		exec(
			("UPDATE {table}" +
			" SET {column} = CASE" +
			"   WHEN id={id} THEN {mId}" +
			"   ELSE null" +
			"  END" +
			" WHERE id = {id} OR {column} = {mId}")
			.replace("{table}", table)
			.replace("{column}", column)
			.replace("{id}", String.valueOf(model.getId()))
			.replace("{mId}", String.valueOf(id))
		);
	}
	
	private void clearOneToOne(ModelAdapter adapter, String field, int id) throws SQLException {
		String table = tableName(adapter.getModelClass());
		String column = safeSqlWord(dbType, columnName(field));
		
		exec("UPDATE " + table + " SET " + column + "=null WHERE " + column + "=" + id);
	}
	
	/**
	 * Update a Many to One (opposite is NOT required) collection
	 */
	private void doUpdateManyToOne(String table, String column, int id, Collection<?> collection) throws SQLException {
		Statement s = null;
		try {
			s = connection.createStatement();

			String sql = "UPDATE " + table + " SET " + column + "=null WHERE " + column + "=" + id;
			logger.trace(sql);
			s.executeUpdate(sql);

			if(!collection.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("UPDATE ").append(table).append(" SET ").append(column).append('=').append(id).append(" WHERE id IN (");
				for(Iterator<?> iter = collection.iterator(); iter.hasNext();) {
					Model model = (Model) iter.next();
					sb.append(model.getId());
					if(iter.hasNext()) {
						sb.append(',');
					}
				}
				sb.append(')');

				sql = sb.toString();
				logger.trace(sql);
				s.executeUpdate(sql);
			}
		} finally {
			s.close();
		}
	}

	public void drop(String schema) throws SQLException {
		String sql = "select t.tablename, c.constraintname" + " from sys.sysconstraints c, sys.systables t"
				+ " where c.type = 'F' and t.tableid = c.tableid";

		List<Map<String, Object>> constraints = asFieldMaps(connection, sql);
		for(Map<String, Object> map : constraints) {
			sql = "alter table " + map.get("tablename") + " drop constraint " + map.get("constraintname");
			logger.trace(sql);
			try {
				connection.createStatement().executeUpdate(sql);
			} catch(Exception e) {
				logger.error(e);
			}
		}

		ResultSet rs = connection.getMetaData().getTables(null, schema, "%", new String[] { "TABLE" });
		while(rs.next()) {
			sql = "drop table " + schema + "." + rs.getString(3);
			logger.trace(sql);
			try {
				connection.createStatement().executeUpdate(sql);
			} catch(Exception e) {
				logger.error(e);
			}
		}
	}

	public List<Map<String, Object>> executeQuery(String sql, Object... values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start executeQuery: " + sql + join(" [", values, "]", ", "));
		}

		Statement s = null;
		try {
			s = doExecuteQuery(sql, values);
			return asFieldMaps(s.getResultSet());
		} finally {
			logger.debug("end executeQuery");
			if(s != null) {
				s.close();
			}
		}
	}

	public List<List<Object>> executeQueryLists(String sql, Object... values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start executeQueryLists: " + sql + join(" [", values, "]", ", "));
		}
		
		Statement s = null;
		try {
			s = doExecuteQuery(sql, values);
			return asLists(s.getResultSet(), true);
		} finally {
			logger.debug("end executeQueryLists");
			if(s != null) {
				s.close();
			}
		}
	}

	public Object executeQueryValue(String sql, Object... values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start executeQueryValue: " + sql + join(" [", values, "]", ", "));
		}

		Statement s = null;
		try {
			ResultSet rs;
			if(values.length == 0) {
				s = connection.createStatement();
				s.setMaxRows(1);
				rs = s.executeQuery(sql);
			} else {
				PreparedStatement ps = (PreparedStatement) (s = connection.prepareStatement(sql));
				ps.setMaxRows(1);
				setStatementValues(ps, values);
				rs = ps.executeQuery();
			}
			if(rs.next()) {
				return SqlUtils.getValue(rs, 1);
			} else {
				return null;
			}
		} finally {
			logger.debug("end executeQueryValue");
			if(s != null) {
				s.close();
			}
		}
	}

	public List<Object> executeQueryValues(String sql, Object... values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start executeQueryValues: " + sql + join(" [", values, "]", ", "));
		}

		Statement s = null;
		try {
			ResultSet rs;
			if(values.length == 0) {
				s = connection.createStatement();
				rs = s.executeQuery(sql);
			} else {
				PreparedStatement ps = (PreparedStatement) (s = connection.prepareStatement(sql));
				setStatementValues(ps, values);
				rs = ps.executeQuery();
			}
			List<Object> results = new ArrayList<Object>();
			while(rs.next()) {
				results.add(SqlUtils.getValue(rs, 1));
			}
			return results;
		} finally {
			logger.debug("end executeQueryValue");
			if(s != null) {
				s.close();
			}
		}
	}

	public int executeUpdate(String sql, Object... values) throws SQLException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			if(isInsert(sql)) {
				if(logger.isLoggingDebug()) {
					logger.debug("start executeUpdate(insert): " + sql + join(" [", values, "]", ", "));
				}
				ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				setStatementValues(ps, values);
				ps.executeUpdate();
				rs = ps.getGeneratedKeys();
				return (rs.next()) ? rs.getInt(1) : -1;
			} else {
				if(logger.isLoggingDebug()) {
					logger.debug("start executeUpdate: " + sql + join(" [", values, "]", ", "));
				}
				ps = connection.prepareStatement(sql);
				setStatementValues(ps, values);
				return ps.executeUpdate();
			}
		} finally {
			logger.debug("end executeUpdate");
			if(ps != null)
				ps.close();
			if(rs != null)
				rs.close();
		}
	}
	
	public <T extends Model> T find(Class<T> clazz, int id) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start find " + clazz.getCanonicalName() + " id: " + id);
		}

		if(id < 1) {
			return null;
		}

		T result = find(clazz, "where id=" + id);

		logger.debug("end find");
		return result;
	}

	public <T extends Model> T find(Class<T> clazz, String sql, Object... values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start find: " + clazz.getCanonicalName() + ", " + sql + join(" <- [", values, "]", ", "));
		}

		QueryProcessor<T> processor = QueryProcessor.create(dbType, clazz, limit(sql, 1), values);
		List<T> list = processor.process(connection);

		T result = list.isEmpty() ? null : list.get(0);

		logger.debug("end find");
		return result;
	}

	public <T extends Model> List<T> findAll(Class<T> clazz, String sql, Object...values) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug("start findAll: " + clazz.getCanonicalName() + ", " + sql + join(" <- [", values, "]", ", "));
		}

		QueryProcessor<T> processor = QueryProcessor.create(dbType, clazz, sql, values);
		List<T> list = processor.process(connection);

		logger.debug("end findAll");
		return list;
	}

	private String getField(Object o) {
		if(o instanceof String) {
			return (String) o;
		}
		if(o instanceof Map<?,?>) {
			return String.valueOf(((Map<?,?>) o).keySet().iterator().next());
		}
		throw new IllegalArgumentException("only expecting String or Map, not: " + o);
	}

	private boolean isCreatedDateTimeField(Cell cell) {
		return CREATED_AT.equals(cell.column) || CREATED_ON.equals(cell.column);
	}
	
	private boolean isDateTimeField(Cell cell) {
		return isCreatedDateTimeField(cell) || isUpdatedDateTimeField(cell);
	}

	private boolean isUpdatedDateTimeField(Cell cell) {
		return UPDATED_AT.equals(cell.column) ||  UPDATED_ON.equals(cell.column);
	}

	public void retrieve(Model[] models) throws SQLException {
		if(logger.isLoggingDebug()) {
			logger.debug(join("start retrieve: [", models, "]", ", "));
		}
		if(models.length == 0) {
			return;
		} else if(models.length == 1) {
			QueryProcessor<?> processor = QueryProcessor.create(dbType, models[0].getClass(), "where id=?", models[0].getId());
			List<?> list = processor.process(connection);
			if(!list.isEmpty()) {
				setFields(models[0], ((Model) list.get(0)).getAll());
			}
		} else {
			Map<Class<? extends Model>, Map<Integer, Model>> map = new HashMap<Class<? extends Model>, Map<Integer,Model>>();
			for(Model model : models) {
				Class<? extends Model> clazz = model.getClass();
				int id = model.getId(int.class);
				Map<Integer, Model> ids = map.get(clazz);
				if(ids == null) {
					map.put(clazz, ids = new HashMap<Integer, Model>());
				}
				ids.put(id, model);
			}
			for(Entry<Class<? extends Model>, Map<Integer, Model>> entry : map.entrySet()) {
				Class<? extends Model> clazz = entry.getKey();
				Map<Integer, Model> modelMap = entry.getValue();
				Set<Integer> ids = modelMap.keySet();
				
				if(logger.isLoggingDebug()) {
					logger.debug("retrieving data from database: " + clazz.getCanonicalName() + StringUtils.join(", id IN (", ids, ")", ", "));
				}

				QueryProcessor<? extends Model> processor = QueryProcessor.create(dbType, clazz, StringUtils.join("id IN (", ids, ")", ","));
				for(Model model : processor.process(connection)) {
					setFields(modelMap.get(model.getId()), model.getAll());
				}
			}
		}
		logger.debug("end retrieve");
	}

	public void retrieve(Model model, String include) throws SQLException {
		logger.debug("start retrieve: {}, include: {}", model, include);

		QueryProcessor<?> processor = QueryProcessor.create(dbType, model.getClass(), "where id=? include:?", model.getId(), include);
		List<?> list = processor.process(connection);
		if(!list.isEmpty()) {
			setFields(model, ((Model) list.get(0)).getAll());
		}

		logger.debug("end retrieve");
	}

	public void retrieveFields(Model model, String fields) throws SQLException {
		logger.debug("start retrieve: {}, fields: {}", model, fields);

		QueryProcessor<?> processor = QueryProcessor.create(dbType, model.getClass(), "where id=? include:?", model.getId(), fields);
		List<?> list = processor.process(connection);
		if(!list.isEmpty()) {
			List<Object> fieldList = toList(fields);
			String[] fieldNames = new String[fieldList.size()];
			for(int i = 0; i < fieldNames.length; i++) {
				fieldNames[i] = getField(fieldList.get(i));
			}
			setFields(model, ((Model) list.get(0)).getAll(), fieldNames);
		}

		logger.debug("end retrieve");
	}
	
	private void setStatementValues(PreparedStatement ps, Object[] values) throws SQLException {
		for(int i = 0; i < values.length; i++) {
			setObject(ps, i + 1, values[i]);
		}
	}

	public void update(Model[] models) throws SQLException, NoSuchFieldException {
		for(Model model : models) {
			doUpdate(model);
		}
	}

}

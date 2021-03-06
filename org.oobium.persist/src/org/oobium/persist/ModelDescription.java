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
package org.oobium.persist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModelDescription {

	public static final String ID = "id";

	public static final String CREATED_AT = "createdAt";
	public static final String UPDATED_AT = "updatedAt";
	public static final String CREATED_ON = "createdOn";
	public static final String UPDATED_ON = "updatedOn";

	public static final boolean DEFAULT_ACTIVATION = false;
	public static final boolean DEFAULT_TIMESTAMPS = false;
	public static final boolean DEFAULT_DATESTAMPS = false;
	public static final boolean DEFAULT_ALLOW_DELETE = true;
	public static final boolean DEFAULT_ALLOW_UPDATE = true;
	public static final boolean DEFAULT_EMBEDDED = false;
	
	
	Attribute[] attrs() default {};
	Relation[] hasMany() default {};
	Relation[] hasOne() default {};
	
	/**
	 * If true, then this model will have "createdOn" and "updatedOn" attributes, both
	 * of type java.sql.Date. The fields will be automatically handled in the persist service.
	 */
	boolean datestamps() default DEFAULT_DATESTAMPS;

	/**
	 * If true, then this model will have "createdAt" and "updatedAt" attributes, both
	 * of type java.util.Date. The fields will be automatically handled in the persist service.
	 */
	boolean timestamps() default DEFAULT_TIMESTAMPS;
	
	boolean allowDelete() default DEFAULT_ALLOW_DELETE;
	boolean allowUpdate() default DEFAULT_ALLOW_UPDATE;

	/**
	 * If true, then this model type is <i>only</i> to be used as an embedded model - standard
	 * finders and other functions that require a separate table/collection will not be generated
	 * and the id field will always be null.
	 * <p>Default is false</p>
	 * @see Relation#embed()
	 */
	boolean embedded() default DEFAULT_EMBEDDED;
	
}

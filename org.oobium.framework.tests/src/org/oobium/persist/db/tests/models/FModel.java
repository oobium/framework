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
/**
 * 
 */
package org.oobium.persist.db.tests.models;

import org.oobium.persist.Attribute;
import org.oobium.persist.Model;
import org.oobium.persist.ModelDescription;
import org.oobium.persist.ModelList;
import org.oobium.persist.Relation;

@ModelDescription(
	attrs = {
		@Attribute(name="fName", type=String.class)
	},
	hasMany = {
		@Relation(name="dModels", type=DModel.class, opposite="fModel")
	}
)
public class FModel extends Model {

//	DynModel fm = DynClasses.getModel("pkg", "FModel")
//											.addAttr("fName", "String.class")
//											.addHasMany("dModels", "DModel.class", "opposite=fModel");

	public String getFName() {
		return (String) get("fName");
	}

	public void setFName(String fName) {
		set("fName", fName);
	}

	public ModelList<DModel> dModels() {
		return (ModelList<DModel>) get("dModels");
	}

}

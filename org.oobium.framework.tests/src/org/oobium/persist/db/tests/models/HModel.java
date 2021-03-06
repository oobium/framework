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
		@Attribute(name="hName", type=String.class)
	},
	hasMany = {
		@Relation(name="gModels", type=GModel.class, opposite="hModel")
	}
)
public class HModel extends Model {

//	DynModel hm = DynClasses.getModel("pkg", "HModel")
//											.addAttr("hName", "String.class")
//											.addHasMany("gModels", "GModel.class", "opposite=hModel");

	public String getHName() {
		return (String) get("hName");
	}

	public void setHName(String hName) {
		set("hName", hName);
	}

	public ModelList<GModel> gModels() {
		return (ModelList<GModel>) get("gModels");
	}

}

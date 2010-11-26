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
package org.oobium.utils.coercion.coercers;

import java.lang.reflect.Method;
import java.util.Map;

import org.oobium.utils.json.JsonModel;

public class JsonModelCoercer extends AbstractCoercer {

	public JsonModel coerce(Object object, Class<? extends JsonModel> toType) {
		if(object instanceof Number) {
			return createModel(((Number) object).intValue(), toType);
		}
		if(object instanceof Map<?,?>) {
			return coerce((Map<?,?>) object, toType);
		}
		throw new UnsupportedOperationException();
	}

	public JsonModel coerce(Map<?,?> map, Class<? extends JsonModel> toType) {
		return createModel(map, toType);
	}
	
	public JsonModel coerce(String string, Class<? extends JsonModel> toType) {
		try {
			int id = Integer.valueOf(string);
			return createModel(id, toType);
		} catch(NumberFormatException e) {
			return createModel(string, toType);
		}
	}

	@Override
	public JsonModel coerceNull() {
		return null;
	}
	
	private JsonModel createModel(int id, Class<? extends JsonModel> toType) {
		try {
			JsonModel model = toType.newInstance();
			Method method = toType.getMethod("setId", int.class);
			method.invoke(model, id);
			return model;
		} catch(Exception e) {
			return null;
		}
	}
	
	private JsonModel createModel(Map<?,?> data, Class<? extends JsonModel> toType) {
		try {
			JsonModel model = toType.newInstance();
			Method method = toType.getMethod("setAll", Map.class);
			method.invoke(model, data);
			return model;
		} catch(Exception e) {
			return null;
		}
	}

	private JsonModel createModel(String json, Class<? extends JsonModel> toType) {
		try {
			JsonModel model = toType.newInstance();
			Method method = toType.getMethod("setAll", String.class);
			method.invoke(model, json);
			return model;
		} catch(Exception e) {
			return null;
		}
	}

	@Override
	public Class<?> getType() {
		return JsonModel.class;
	}
	
	@Override
	public boolean handleSubTypes() {
		return true;
	}

}

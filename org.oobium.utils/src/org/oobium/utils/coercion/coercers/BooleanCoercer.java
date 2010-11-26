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


public class BooleanCoercer extends AbstractCoercer {

	public Boolean coerce(String string, Class<?> toType) {
		return new Boolean(string);
	}
	
	public Boolean coerce(Boolean b, Class<?> toType) {
		return b;
	}
	
	@Override
	public Class<?> getType() {
		return Boolean.class;
	}

}

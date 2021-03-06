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
package org.oobium.app.controllers;

public interface IFlash {

	public abstract Object flash(String name);
	public abstract <T> T flash(String name, Class<T> type);
	public <T> T flash(String name, T defaultValue);

	public abstract Object getFlash(String name);
	public abstract <T> T getFlash(String name, Class<T> type);
	public <T> T getFlash(String name, T defaultValue);
	public abstract boolean hasFlash(String name);
	
	public abstract Object getFlashError();
	public abstract boolean hasFlashError();
	
	public abstract Object getFlashNotice();
	public abstract boolean hasFlashNotice();
	
	public abstract Object getFlashWarning();
	public abstract boolean hasFlashWarning();
	
}

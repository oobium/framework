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
package org.oobium.console;

import org.eclipse.swt.SWT;

public abstract class Function {

	/**
	 * The value of this property will be SWT.COMMAND on OSX (carbon and cocoa), and SWT.CONTROL on other platforms.
	 * Use this rather than SWT.CONTROL or SWT.COMMAND unless you really need to differentiate.
	 */
	public static final int COMMAND;
	static {
		String platform = SWT.getPlatform();
		if(platform.equals("cocoa") || platform.equals("carbon")) {
			COMMAND = SWT.COMMAND;
		} else {
			COMMAND = SWT.CONTROL;
		}
	}
	
	
	protected Console console;
	public final int stateMask;
	public final int keyCode;
	
	public Function(char c) {
		this(SWT.NONE, c);
	}
	
	public Function(int keyCode) {
		this(SWT.NONE, keyCode);
	}
	
	public Function(int stateMask, char c) {
		this.stateMask = stateMask;
		this.keyCode = (int) c - (int) 'a' + 97;
	}
	
	public Function(int stateMask, int keyCode) {
		this.stateMask = stateMask;
		this.keyCode = keyCode;
	}
	
	public abstract void execute();
	
}

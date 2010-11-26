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
package org.oobium.eclipse.views.developer.actions;

import org.eclipse.jface.action.Action;
import org.oobium.eclipse.OobiumPlugin;
import org.oobium.eclipse.views.developer.ConsoleView;

public class LinkedAction extends Action {

	private ConsoleView view;
	
	public LinkedAction(ConsoleView view) {
		super("Link", AS_CHECK_BOX);
		setToolTipText("Link with Package Explorer");
		setImageDescriptor(OobiumPlugin.getImageDescriptor("/icons/synced.gif"));
		this.view = view;
	}
	
	@Override
	public void run() {
		view.setLinked(isChecked());
	}
	
}

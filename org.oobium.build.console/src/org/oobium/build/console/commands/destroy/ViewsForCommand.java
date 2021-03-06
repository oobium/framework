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
package org.oobium.build.console.commands.destroy;

import java.io.File;

import org.oobium.build.console.BuilderCommand;
import org.oobium.build.console.Eclipse;
import org.oobium.build.workspace.Module;
import org.oobium.utils.FileUtils;

public class ViewsForCommand extends BuilderCommand {

	@Override
	public void configure() {
		moduleRequired = true;
		maxParams = 1;
		minParams = 1;
	}

	@Override
	public void run() {
		Module module = getModule();
		File views = module.getViewsFolder(param(0));
		if(!views.exists()) {
			console.err.println("views folder for " + param(0) + "does not exist");
			return;
		}
		
		String confirm = flag('f') ? "Y" : ask("Permanently remove the views folder and all contents?[Y/N] ");
		if(!confirm.equalsIgnoreCase("Y")) {
			console.out.println("operation cancelled");
			return;
		}

		FileUtils.delete(module.getViewsFolder(param(0)));

		Eclipse.refreshProject(module.name);
	}
	
}

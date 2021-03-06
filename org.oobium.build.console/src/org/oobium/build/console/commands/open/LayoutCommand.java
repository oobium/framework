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
package org.oobium.build.console.commands.open;

import java.io.File;

import org.oobium.build.console.BuilderCommand;
import org.oobium.build.console.Eclipse;
import org.oobium.build.workspace.Module;

public class LayoutCommand extends BuilderCommand {

	@Override
	public void run() {
		Module module = getModule();
		if(module == null) {
			module = getApplication();
			if(module == null) {
				console.err.println("bundle is not set to a module and is not within an application");
				return;
			}
		}
		File layout = module.getLayout();
		if(layout.exists()) {
			Eclipse.openFile(module.file, layout);
		} else {
			console.err.println("layout does not exist");
		}
	}

}

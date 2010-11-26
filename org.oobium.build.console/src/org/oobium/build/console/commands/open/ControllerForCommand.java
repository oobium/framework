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
import org.oobium.build.console.BuilderConsoleActivator;
import org.oobium.build.workspace.Module;

public class ControllerForCommand extends BuilderCommand {

	@Override
	public void configure() {
		moduleRequired = true;
		maxParams = 1;
		minParams = 1;
	}
	
	@Override
	public void run() {
		File model;
		String name;
		
		String[] sa = param(0).split("#");
		if(sa.length == 1) {
			Module module = getModule();
			model = module.getModel(sa[0]);
			name = module.getModelName(model);
		} else {
			Module module = getWorkspace().getModule(sa[0]);
			if(module == null) {
				console.err.println("module " + sa[0] + " does not exist");
				return;
			}
			if(module.isJar) {
				console.err.println("jarred modules not yet supported");
				return;
			}
			model = module.getModel(sa[1]);
			name = module.getModelName(model);
		}
		
		Module module = getModule();
		
		if(!model.isFile()) {
			console.err.println("model " + name + " does not exist");
			return;
		}
		
		File controller = module.getControllerFor(name);
		if(controller.exists()) {
//			console.out.println("opening " + file);
			BuilderConsoleActivator.sendOpen(module, controller);
		} else {
			console.err.println("controller does not exist");
		}
	}

}

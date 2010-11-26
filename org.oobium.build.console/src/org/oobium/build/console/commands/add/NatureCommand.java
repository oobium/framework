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
package org.oobium.build.console.commands.add;

import org.oobium.build.console.BuilderCommand;
import org.oobium.build.workspace.Bundle;

public class NatureCommand extends BuilderCommand {

	@Override
	public void configure() {
		bundleRequired = true;
		maxParams = 1;
		minParams = 1;
	}
	
	@Override
	public void run() {
		Bundle bundle = getBundle();
		if(bundle.isJar) {
			console.err.println("Project is a jar; Natures are only relevant to source projects");
			return;
		}
		if(!bundle.project.exists()) {
			console.err.println("Project is a jar; Natures are only relevant to source projects");
			return;
		}
		
		bundle.addNature(param(0));
	}

}

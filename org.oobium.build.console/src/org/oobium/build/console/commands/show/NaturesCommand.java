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
package org.oobium.build.console.commands.show;

import org.oobium.build.console.BuilderCommand;
import org.oobium.build.workspace.Project;

public class NaturesCommand extends BuilderCommand {

	@Override
	public void configure() {
		bundleRequired = true;
	}

	@Override
	public void run() {
		Project project = getProject();
		if(project.isJar) {
			console.err.println("Project is a jar; Natures are only relevant to source projects");
		} else {
			for(String nature : project.getNatures()) {
				console.out.println(nature);
			}
		}
	}

}

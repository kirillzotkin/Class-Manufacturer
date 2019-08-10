package org.enterprisedomain.classmaker.jobs.install;

import org.eclipse.emf.common.util.EList;
import org.enterprisedomain.classmaker.impl.CustomizerImpl;

public class CreateInstallerCustomizer extends CustomizerImpl {

	@Override
	public Object customize(EList<Object> args) {
		return new OSGiInstaller((Long) args.get(0));
	}

}

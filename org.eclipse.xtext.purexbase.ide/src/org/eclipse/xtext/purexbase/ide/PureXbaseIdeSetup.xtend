/*
 * generated by Xtext
 */
package org.eclipse.xtext.purexbase.ide

import com.google.inject.Guice
import org.eclipse.xtext.purexbase.PureXbaseRuntimeModule
import org.eclipse.xtext.purexbase.PureXbaseStandaloneSetup
import org.eclipse.xtext.util.Modules2

/**
 * Initialization support for running Xtext languages as language servers.
 */
class PureXbaseIdeSetup extends PureXbaseStandaloneSetup {

	override createInjector() {
		Guice.createInjector(Modules2.mixin(new PureXbaseRuntimeModule, new PureXbaseIdeModule))
	}
	
}

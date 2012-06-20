/*******************************************************************************
 * Copyright (c) 2012 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.typesystem.util;

import java.util.Map;
import java.util.Set;

import org.eclipse.xtext.common.types.JvmTypeParameter;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.xtype.XComputedTypeReference;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 * TODO JavaDoc, toString
 */
public class UnboundTypeParameterPreservingSubstitutor extends TypeParameterSubstitutor {
	
	public UnboundTypeParameterPreservingSubstitutor(Map<JvmTypeParameter, MergedBoundTypeArgument> typeParameterMapping,
			CommonTypeComputationServices services) {
		super(typeParameterMapping, services);
	}

	@Override
	public JvmTypeReference doVisitComputedTypeReference(XComputedTypeReference reference,
			Set<JvmTypeParameter> param) {
		if (UnboundTypeParameters.isUnboundTypeParameter(reference)) {
			XComputedTypeReference result = getServices().getXtypeFactory().createXComputedTypeReference();
			result.setTypeProvider(reference.getTypeProvider());
			return result;
		}
		return super.doVisitComputedTypeReference(reference, param);
	}
}
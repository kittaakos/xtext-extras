/*******************************************************************************
 * Copyright (c) 2012 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.typesystem.internal;

import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.typesystem.references.BaseTypeComputationResult;
import org.eclipse.xtext.xbase.typesystem.references.LightweightTypeReference;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 * TODO JavaDoc, toString
 */
public class NoTypeResult extends BaseTypeComputationResult {

	public XExpression getExpression() {
		return null;
	}
	
	public LightweightTypeReference internalGetActualExpressionType() {
		throw new UnsupportedOperationException("TODO implement me");
	}
	
	public LightweightTypeReference internalGetExpectedExpressionType() {
		throw new UnsupportedOperationException("TODO implement me");
	}

	public LightweightTypeReference internalGetActualType(JvmIdentifiableElement element) {
		throw new UnsupportedOperationException("TODO implement me");
	}

}

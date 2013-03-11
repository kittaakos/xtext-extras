/*******************************************************************************
 * Copyright (c) 2012 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.typesystem.internal;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.xbase.XAbstractFeatureCall;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.XbasePackage;
import org.eclipse.xtext.xbase.scoping.batch.IFeatureScopeSession;
import org.eclipse.xtext.xbase.typesystem.computation.IFeatureLinkingCandidate;
import org.eclipse.xtext.xbase.typesystem.conformance.ConformanceHint;
import org.eclipse.xtext.xbase.typesystem.references.LightweightTypeReference;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 * TODO JavaDoc, toString
 */
@NonNullByDefault
public class ExpressionTypeComputationState extends AbstractStackedTypeComputationState {

	protected class ExpressionAwareTypeCheckpointComputationState extends TypeCheckpointComputationState {
		protected ExpressionAwareTypeCheckpointComputationState(ResolvedTypes resolvedTypes, IFeatureScopeSession featureScopeSession,
				AbstractTypeComputationState parent) {
			super(resolvedTypes, featureScopeSession, parent);
		}

		@Override
		protected ExpressionAwareStackedResolvedTypes doComputeTypes(XExpression expression) {
			markAsPropagated();
			return super.doComputeTypes(expression);
		}
		
		@Override
		public TypeCheckpointComputationState withTypeCheckpoint(@Nullable EObject context) {
			return new ExpressionAwareTypeCheckpointComputationState(getResolvedTypes(), getFeatureScopeSession(), this);
		}
	}

	protected final XExpression expression;

	protected ExpressionTypeComputationState(StackedResolvedTypes resolvedTypes,
			IFeatureScopeSession featureScopeSession,
			AbstractTypeComputationState parent,
			XExpression expression) {
		super(resolvedTypes, featureScopeSession, parent);
		this.expression = expression;
	}
	
	@Override
	protected ExpressionAwareStackedResolvedTypes doComputeTypes(XExpression expression) {
		if (expression == this.expression) {
			throw new IllegalArgumentException("Attempt to compute the type of the currently computed expression: " + expression);
		}
		markAsPropagated();
		return super.doComputeTypes(expression);
	}
	
	protected void markAsPropagated() {
		getResolvedTypes().setPropagatedType(this.expression);
	}

	@Override
	protected ExpressionAwareStackedResolvedTypes pushTypes(XExpression expression) {
		return getResolvedTypes().pushTypes(expression);
	}

	@Override
	protected LightweightTypeReference acceptType(ResolvedTypes resolvedTypes, AbstractTypeExpectation expectation, LightweightTypeReference type, boolean returnType, ConformanceHint... hints) {
		LightweightTypeReference result = resolvedTypes.acceptType(expression, expectation, type, returnType, hints);
		getParent().acceptType(expression, resolvedTypes, expectation, type, returnType, hints);
		return result;
	}
	
	@Override
	protected LightweightTypeReference acceptType(XExpression expression, ResolvedTypes resolvedTypes, AbstractTypeExpectation expectation, LightweightTypeReference type, boolean returnType, ConformanceHint... hints) {
		if (expression != this.expression) {
			LightweightTypeReference result = resolvedTypes.acceptType(this.expression, expectation, type, returnType, hints);
			getParent().acceptType(this.expression, resolvedTypes, expectation, type, returnType, hints);
			return result;
		}
		return getParent().acceptType(expression, resolvedTypes, expectation, type, returnType, hints);
	}
	
	@Override
	public TypeComputationStateWithExpectation withExpectation(@Nullable LightweightTypeReference expectation) {
		return new ExpressionTypeComputationStateWithExpectation(getResolvedTypes(), getFeatureScopeSession(), this, expectation);
	}
	
	@Override
	public AbstractTypeComputationState withoutExpectation() {
		return new ExpressionTypeComputationStateWithExpectation(getResolvedTypes(), getFeatureScopeSession(), this, null);
	}
	
	@Override
	public TypeCheckpointComputationState withTypeCheckpoint(@Nullable EObject context) {
		return new ExpressionAwareTypeCheckpointComputationState(getResolvedTypes(), getFeatureScopeSession(), this);
	}
	
	@Override
	protected IFeatureLinkingCandidate createResolvedLink(XAbstractFeatureCall featureCall,
			JvmIdentifiableElement resolvedTo) {
		if (isImplicitReceiver(featureCall)) {
			return new ResolvedImplicitReceiver((XAbstractFeatureCall) featureCall.eContainer(), featureCall, this);
		}
		if (isImplicitFirstArgument(featureCall)) {
			return new ResolvedImplicitFirstArgument((XAbstractFeatureCall) featureCall.eContainer(), featureCall, this);
		}
		return super.createResolvedLink(featureCall, resolvedTo);
	}
	
	protected boolean isImplicitReceiver(XAbstractFeatureCall featureCall) {
		boolean result = featureCall.eContainingFeature() == XbasePackage.Literals.XABSTRACT_FEATURE_CALL__IMPLICIT_RECEIVER;
		return result;
	}
	
	protected boolean isImplicitFirstArgument(XAbstractFeatureCall featureCall) {
		boolean result = featureCall.eContainingFeature() == XbasePackage.Literals.XABSTRACT_FEATURE_CALL__IMPLICIT_FIRST_ARGUMENT;
		return result;
	}
	
	protected boolean isInstanceContext() {
		return getFeatureScopeSession().isInstanceContext();
	}

	protected StackedResolvedTypes getStackedResolvedTypes() {
		return (StackedResolvedTypes) resolvedTypes;
	}
	
}

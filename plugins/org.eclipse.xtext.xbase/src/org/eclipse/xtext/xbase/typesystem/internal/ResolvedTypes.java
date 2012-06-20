/*******************************************************************************
 * Copyright (c) 2012 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.typesystem.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.common.types.JvmCompoundTypeReference;
import org.eclipse.xtext.common.types.JvmConstructor;
import org.eclipse.xtext.common.types.JvmDelegateTypeReference;
import org.eclipse.xtext.common.types.JvmField;
import org.eclipse.xtext.common.types.JvmGenericArrayTypeReference;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.common.types.JvmMultiTypeReference;
import org.eclipse.xtext.common.types.JvmOperation;
import org.eclipse.xtext.common.types.JvmParameterizedTypeReference;
import org.eclipse.xtext.common.types.JvmSpecializedTypeReference;
import org.eclipse.xtext.common.types.JvmTypeConstraint;
import org.eclipse.xtext.common.types.JvmTypeParameter;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.common.types.JvmWildcardTypeReference;
import org.eclipse.xtext.common.types.TypesFactory;
import org.eclipse.xtext.common.types.TypesPackage;
import org.eclipse.xtext.common.types.util.ITypeReferenceVisitor;
import org.eclipse.xtext.common.types.util.TypeConformanceComputer;
import org.eclipse.xtext.xbase.XAbstractFeatureCall;
import org.eclipse.xtext.xbase.XClosure;
import org.eclipse.xtext.xbase.XConstructorCall;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.typesystem.computation.ConformanceHint;
import org.eclipse.xtext.xbase.typesystem.computation.IConstructorLinkingCandidate;
import org.eclipse.xtext.xbase.typesystem.computation.IFeatureLinkingCandidate;
import org.eclipse.xtext.xbase.typesystem.computation.ILinkingCandidate;
import org.eclipse.xtext.xbase.typesystem.references.BaseResolvedTypes;
import org.eclipse.xtext.xbase.typesystem.references.LightweightTypeReference;
import org.eclipse.xtext.xbase.typesystem.references.OwnedConverter;
import org.eclipse.xtext.xbase.typesystem.util.AbstractReentrantTypeReferenceProvider;
import org.eclipse.xtext.xbase.typesystem.util.CommonTypeComputationServices;
import org.eclipse.xtext.xbase.typesystem.util.MergedBoundTypeArgument;
import org.eclipse.xtext.xbase.typesystem.util.MultimapJoiner;
import org.eclipse.xtext.xbase.typesystem.util.TypeParameterByConstraintSubstitutor;
import org.eclipse.xtext.xbase.typesystem.util.UnboundTypeParameter;
import org.eclipse.xtext.xbase.typesystem.util.UnboundTypeParameterPreservingSubstitutor;
import org.eclipse.xtext.xbase.typesystem.util.UnboundTypeParameters;
import org.eclipse.xtext.xtype.XComputedTypeReference;
import org.eclipse.xtext.xtype.XFunctionTypeRef;
import org.eclipse.xtext.xtype.XtypeFactory;
import org.eclipse.xtext.xtype.util.AbstractXtypeReferenceVisitor;

import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 * TODO JavaDoc
 */
public abstract class ResolvedTypes extends BaseResolvedTypes {

	private final DefaultReentrantTypeResolver resolver;
	
	private Map<JvmIdentifiableElement, LightweightTypeReference> types;
	private Map<JvmIdentifiableElement, LightweightTypeReference> reassignedTypes;
	private Multimap<XExpression, TypeData> expressionTypes;
	private Map<XExpression, ILinkingCandidate<?>> featureLinking;
	private Map<Object, BaseUnboundTypeParameter> unboundTypeParameters;
	
	protected ResolvedTypes(DefaultReentrantTypeResolver resolver) {
		this.resolver = resolver;
	}
	
	@Override
	protected OwnedConverter getConverter() {
		return super.getConverter();
	}
	
	public CommonTypeComputationServices getServices() {
		return resolver.getServices();
	}

	public List<Diagnostic> getQueuedDiagnostics() {
		throw new UnsupportedOperationException("TODO implement me");
	}
	
	protected TypeData getTypeData(XExpression expression, boolean returnType) {
		Collection<TypeData> values = doGetTypeData(expression);
		if (values == null) {
			return null;
		}
		TypeData result = mergeTypeData(expression, values, returnType, false);
		return result;
	}
	
	protected Collection<TypeData> doGetTypeData(XExpression expression) {
		Collection<TypeData> result = ensureExpressionTypesMapExists().get(expression);
		if (result.isEmpty())
			return null;
		return result;
	}

	protected TypeData mergeTypeData(final XExpression expression, Collection<TypeData> allValues, final boolean returnType, boolean nullIfEmpty) {
		List<TypeData> values = Lists.newArrayListWithCapacity(allValues.size());
		for(TypeData value: allValues) {
			if (returnType == value.isReturnType()) {
				values.add(value);
			}
		}
		if (values.isEmpty() && nullIfEmpty) {
			return null;
		}
		if (values.size() == 1) {
			TypeData typeData = values.get(0);
			return typeData;
		}
		final XComputedTypeReference mergedType = getXtypeFactory().createXComputedTypeReference();
		mergedType.setTypeProvider(new AbstractReentrantTypeReferenceProvider() {
			@Override
			protected LightweightTypeReference doGetTypeReference() {
				Collection<TypeData> freshlyObtainedValues = ensureExpressionTypesMapExists().get(expression);
				List<LightweightTypeReference> references = Lists.newArrayList();
				for(TypeData value: freshlyObtainedValues) {
					LightweightTypeReference reference = value.getActualType();
					if (returnType == value.isReturnType() && isValidForMergedResult(reference, mergedType)) {
						references.add(reference);
					}
				}
				return getMergedType(references);
			}
		});
		TypeData result = new TypeData(expression, null /* TODO use all expectations? */, mergedType, ConformanceHint.MERGED /* TODO do we need that? */, returnType);
		return result;
	}
	
	protected XtypeFactory getXtypeFactory() {
		return getResolver().getServices().getXtypeFactory();
	}

	protected boolean isValidForMergedResult(LightweightTypeReference reference, LightweightTypeReference mayNotBe) {
		if (reference == mayNotBe || reference == null)
			return false;
		if (reference instanceof JvmDelegateTypeReference) {
			return isValidForMergedResult(((JvmDelegateTypeReference) reference).getDelegate(), mayNotBe);
		}
		if (reference instanceof JvmCompoundTypeReference) {
			List<LightweightTypeReference> components = ((JvmCompoundTypeReference) reference).getReferences();
			if (components.isEmpty())
				return false;
			for(LightweightTypeReference component: components) {
				if (!isValidForMergedResult(component, mayNotBe)) {
					return false;
				}
			}
		}
		if (reference instanceof JvmSpecializedTypeReference) {
			// filter reentrant, recursive type deps
			return isValidForMergedResult(((JvmSpecializedTypeReference) reference).getEquivalent(), mayNotBe);
		}
		return true;
	}

	protected LightweightTypeReference getMergedType(List<LightweightTypeReference> types) {
		if (types.isEmpty()) {
			return null;
		}
		if (types.size() == 1) {
			LightweightTypeReference result = types.get(0);
			return result;
		}
		LightweightTypeReference result = getTypeConformanceComputer().getCommonSuperType(types);
		if (result != null || types.isEmpty()) {
			return result;
		}
		// common type of JvmAnyType and JvmVoid may be null ... use JvmAnyType in that case
		for (LightweightTypeReference type: types) {
			if (!resolver.getServices().getTypeReferences().is(type, Void.TYPE)) {
				return type;
			}
		}
		return types.get(0);
	}

	protected TypeConformanceComputer getTypeConformanceComputer() {
		return getResolver().getServices().getTypeConformanceComputer();
	}

	public LightweightTypeReference internalGetActualType(XExpression expression) {
		TypeData typeData = getTypeData(expression, false);
		if (typeData != null)
			return typeData.getActualType();
		return null;
	}

	public LightweightTypeReference internalGetExpectedType(XExpression expression) {
		TypeData typeData = getTypeData(expression, false);
		if (typeData != null)
			return typeData.getExpectation().internalGetExpectedType();
		return null;
	}
	
	public List<LightweightTypeReference> internalGetActualTypeArguments(XExpression expression) {
		throw new UnsupportedOperationException("TODO implement me");
	}
	
	public void setType(JvmIdentifiableElement identifiable, JvmTypeReference reference) {
		setType(identifiable, getConverter().toLightweightReference(reference));
	}
	
	public void setType(JvmIdentifiableElement identifiable, LightweightTypeReference reference) {
		ensureTypesMapExists().put(identifiable, reference);
	}
	
	public void reassignType(JvmIdentifiableElement identifiable, JvmTypeReference reference) {
		reassignType(identifiable, getConverter().toLightweightReference(reference));
	}
	
	public void reassignType(JvmIdentifiableElement identifiable, LightweightTypeReference reference) {
		if (reference != null) {
			LightweightTypeReference actualType = internalGetActualType(identifiable);
			if (!getTypeConformanceComputer().isConformant(reference, actualType)) {
				if (getTypeConformanceComputer().isConformant(actualType, reference)) {
					ensureReassignedTypesMapExists().put(identifiable, reference);
				} else {
					JvmMultiTypeReference multiTypeReference = TypesFactory.eINSTANCE.createJvmMultiTypeReference();
					if (actualType instanceof JvmMultiTypeReference) {
						for(LightweightTypeReference component: ((JvmMultiTypeReference) actualType).getReferences()) {
							multiTypeReference.getReferences().add(EcoreUtil2.cloneIfContained(component));
						}
					} else {
						multiTypeReference.getReferences().add(EcoreUtil2.cloneIfContained(actualType));
					}
					multiTypeReference.getReferences().add(EcoreUtil2.cloneIfContained(reference));
					ensureReassignedTypesMapExists().put(identifiable, multiTypeReference);
				}
			}
		} else {
			ensureReassignedTypesMapExists().remove(identifiable);
		}
	}
	
	protected LightweightTypeReference acceptType(final XExpression expression, AbstractTypeExpectation expectation, LightweightTypeReference type, ConformanceHint conformanceHint, boolean returnType) {
		// TODO guard asserter - should only be active in tests
		ITypeReferenceVisitor<Boolean> asserter = new AbstractXtypeReferenceVisitor<Boolean>() {
			@Override
			public Boolean doVisitTypeReference(LightweightTypeReference reference) {
				return Boolean.FALSE;
			}
			@Override
			protected Boolean handleNullReference() {
				return Boolean.FALSE;
			}
			@Override
			public Boolean doVisitFunctionTypeReference(XFunctionTypeRef reference) {
				for(LightweightTypeReference paramType: reference.getParamTypes()) {
					if (visit(paramType))
						return Boolean.TRUE;
				}
				if (visit(reference.getReturnType()))
					return Boolean.TRUE;
				return Boolean.FALSE;
			}
			@Override
			public Boolean doVisitCompoundTypeReference(JvmCompoundTypeReference reference) {
				for(LightweightTypeReference component: reference.getReferences()) {
					if (visit(component))
						return Boolean.TRUE;
				}
				return Boolean.FALSE;
			}
			@Override
			public Boolean doVisitComputedTypeReference(XComputedTypeReference reference) {
				if (reference.getTypeProvider() instanceof UnboundTypeParameter) {
					BaseUnboundTypeParameter unboundTypeParameter = (BaseUnboundTypeParameter) reference.getTypeProvider();
					if (unboundTypeParameters != null) {
						if (unboundTypeParameters.containsKey(unboundTypeParameter.getHandle())) {
							if (!(unboundTypeParameter instanceof RootUnboundTypeParameter)) {
								throw new IllegalStateException("unboundTypeParameter must be a RootUnboundTypeParameter");
							}
						}
					}
					getUnboundTypeParameter(unboundTypeParameter.getHandle());
					for(Object other: unboundTypeParameter.getEquallyBoundHandles())
						getUnboundTypeParameter(other);
				}
				return Boolean.FALSE;
			}
			@Override
			public Boolean doVisitGenericArrayTypeReference(JvmGenericArrayTypeReference reference) {
				if (visit(reference.getComponentType()))
					return Boolean.TRUE;
				return Boolean.FALSE;
			}
			@Override
			public Boolean doVisitWildcardTypeReference(JvmWildcardTypeReference reference) {
				for(JvmTypeConstraint constraint: reference.getConstraints()) {
					if (visit(constraint.getTypeReference()))
						return Boolean.TRUE;
				}
				return Boolean.FALSE;
			}
			@Override
			public Boolean doVisitParameterizedTypeReference(JvmParameterizedTypeReference reference) {
				for(LightweightTypeReference argument: reference.getArguments()) {
					if (visit(argument))
						return Boolean.TRUE;
				}
				return Boolean.FALSE;
			}
		};
		asserter.visit(type);
		if (expectation != null)
			asserter.visit(expectation.getExpectedType());
		
//		AbstractTypeComputationState state = expectation.getState();
		// expectation is type parameter - type is actual - bind type
		// no expectation, type wrap type and add pending type parameter data that should be resolved later on demand
		// this will resolve them to their type parameter constraints if any and no other thing is available
		// mind the conformance hint
		
		TypeParameterByConstraintSubstitutor substitutor = new TypeParameterByConstraintSubstitutor(Collections.<JvmTypeParameter, MergedBoundTypeArgument>emptyMap(), resolver.getServices()) {
			@Override
			protected LightweightTypeReference getUnmappedSubstitute(JvmParameterizedTypeReference reference,
					JvmTypeParameter type, Set<JvmTypeParameter> visiting) {
				// TODO extract method 'isExpressionWithTypeArguments'
				if (expression instanceof XAbstractFeatureCall || expression instanceof XConstructorCall || expression instanceof XClosure) {
					XComputedTypeReference result = getServices().getXtypeFactory().createXComputedTypeReference();
					UnboundTypeParameter typeParameter = createUnboundTypeParameter(expression, type);
					result.setTypeProvider(typeParameter);
					return result;
				} else {
					throw new IllegalStateException("expression was: " + expression);
				}
			}
			
			@Override
			public LightweightTypeReference doVisitComputedTypeReference(final XComputedTypeReference reference,
					Set<JvmTypeParameter> visiting) {
				LightweightTypeReference equivalent = (LightweightTypeReference) reference.eGet(TypesPackage.Literals.JVM_SPECIALIZED_TYPE_REFERENCE__EQUIVALENT, false);
				if (equivalent != null)
					return visit(equivalent, visiting);
				XComputedTypeReference result = getServices().getXtypeFactory().createXComputedTypeReference();
				ILightweightTypeReferenceProvider typeProvider = reference.getTypeProvider();
				if (typeProvider instanceof UnboundTypeParameter) {
					if (((UnboundTypeParameter) typeProvider).isComputed()) {
						return visit(reference.getEquivalent(), visiting);
					}
					result.setTypeProvider(reference.getTypeProvider());
				} else {
					result.setTypeProvider(new AbstractReentrantTypeReferenceProvider() {
						@Override
						protected LightweightTypeReference doGetTypeReference() {
							LightweightTypeReference originalEquivalent = reference.getEquivalent();
							LightweightTypeReference result = substitute(originalEquivalent);
							return result;
						}
					});
				}
				return result;
			}
			
		};
		LightweightTypeReference actualType = substitutor.substitute(type);
		ensureExpressionTypesMapExists().put(expression, new TypeData(expression, expectation, actualType, conformanceHint, returnType));
		return actualType;
	}

	protected Map<JvmIdentifiableElement, LightweightTypeReference> ensureTypesMapExists() {
		if (types == null) {
			types = Maps.newLinkedHashMap();
		}
		return types;
	}
	
	protected Map<JvmIdentifiableElement, LightweightTypeReference> ensureReassignedTypesMapExists() {
		if (reassignedTypes == null) {
			reassignedTypes = Maps.newLinkedHashMap();
		}
		return reassignedTypes;
	}
	
	protected Multimap<XExpression, TypeData> ensureExpressionTypesMapExists() {
		if (expressionTypes == null) {
			expressionTypes = createExpressionTypesMap();
		}
		return expressionTypes;
	}

	protected Multimap<XExpression, TypeData> createExpressionTypesMap() {
		return LinkedHashMultimap.create(2, 2);
	}
	
	protected Map<XExpression, ILinkingCandidate<?>> ensureLinkingMapExists() {
		if (featureLinking == null) {
			featureLinking = Maps.newLinkedHashMap();
		}
		return featureLinking;
	}
	
	public LightweightTypeReference internalGetActualType(JvmIdentifiableElement identifiable) {
		if (reassignedTypes != null) {
			LightweightTypeReference result = reassignedTypes.get(identifiable);
			if (result != null) {
				return result;
			}
		}
		if (types == null)
			return getDeclaredType(identifiable);
		LightweightTypeReference result = ensureTypesMapExists().get(identifiable);
		if (result == null) {
			return getDeclaredType(identifiable);
		}
		return result;
	}
	
	protected LightweightTypeReference getDeclaredType(JvmIdentifiableElement identifiable) {
		if (identifiable instanceof JvmOperation) {
			return getConverter().toLightweightReference(((JvmOperation) identifiable).getReturnType());
		}
		if (identifiable instanceof JvmField) {
			return getConverter().toLightweightReference(((JvmField) identifiable).getType());
		}
		if (identifiable instanceof JvmConstructor) {
			return getConverter().toLightweightReference(resolver.getServices().getTypeReferences().createTypeRef(((JvmConstructor) identifiable).getDeclaringType()));
		}
		return null;
	}
	
	public IFeatureLinkingCandidate getFeature(XAbstractFeatureCall featureCall) {
		return (IFeatureLinkingCandidate) ensureLinkingMapExists().get(featureCall);
	}
	
	public IConstructorLinkingCandidate getConstructor(XConstructorCall constructorCall) {
		return (IConstructorLinkingCandidate) ensureLinkingMapExists().get(constructorCall);
	}

	public void acceptLinkingInformation(XExpression expression, ILinkingCandidate<?> candidate) {
		ensureLinkingMapExists().put(expression, candidate);
	}

	protected DefaultReentrantTypeResolver getResolver() {
		return resolver;
	}
	
	@NonNull
	protected BaseUnboundTypeParameter getUnboundTypeParameter(@NonNull Object handle) {
		BaseUnboundTypeParameter result = ensureTypeParameterMapExists().get(handle);
		if (result == null) {
			throw new IllegalStateException("Could not find type parameter");
		}
		return result;
	}

	@NonNull
	protected RootUnboundTypeParameter createUnboundTypeParameter(@NonNull XExpression expression, @NonNull JvmTypeParameter type) {
		RootUnboundTypeParameter result = new RootUnboundTypeParameter(expression, type, this);
		ensureTypeParameterMapExists().put(result.getHandle(), result);
		return result;
	}
	
	protected Map<Object, BaseUnboundTypeParameter> ensureTypeParameterMapExists() {
		if (unboundTypeParameters == null) {
			unboundTypeParameters = Maps.newLinkedHashMap();
		}
		return unboundTypeParameters;
	}

	protected UnboundTypeParameterPreservingSubstitutor createSubstitutor(
			Map<JvmTypeParameter, MergedBoundTypeArgument> typeParameterMapping) {
		return new UnboundTypeParameterPreservingSubstitutor(typeParameterMapping, resolver.getServices()) {
			@Override
			public LightweightTypeReference doVisitComputedTypeReference(XComputedTypeReference reference, Set<JvmTypeParameter> param) {
				if (UnboundTypeParameters.isUnboundTypeParameter(reference)) {
					XComputedTypeReference result = getServices().getXtypeFactory().createXComputedTypeReference();
					UnboundTypeParameter unboundTypeParameter = (UnboundTypeParameter) reference.getTypeProvider();
					BaseUnboundTypeParameter stacked = getUnboundTypeParameter(unboundTypeParameter.getHandle());
					result.setTypeProvider(stacked);
					return result;
				}
				return super.doVisitComputedTypeReference(reference, param);
			}
		};
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder(getClass().getSimpleName()).append(": [");
		appendContent(result, "  ");
		closeBracket(result);
		return result.toString();
	}

	protected void closeBracket(StringBuilder result) {
		if (result.charAt(result.length() - 1) != '[')
			result.append("\n]");
		else
			result.append("]");
	}

	protected void appendContent(StringBuilder result, String indentation) {
		appendContent(types, "types", result, indentation);
		appendContent(reassignedTypes, "reassignedTypes", result, indentation);
		appendContent(expressionTypes, "expressionTypes", result, indentation);
		appendContent(featureLinking, "featureLinking", result, indentation);
		appendContent(unboundTypeParameters, "unboundTypeParameters", result, indentation);
	}

	protected void appendContent(Map<?, ?> map, String prefix, StringBuilder result, String indentation) {
		if (map != null) {
			MapJoiner joiner = Joiner.on("\n" + indentation).withKeyValueSeparator(" -> ");
			result.append("\n" + indentation).append(prefix).append(": ");
			joiner.appendTo(result, map);
		}
	}
	
	protected void appendContent(Multimap<?, ?> map, String prefix, StringBuilder result, String indentation) {
		if (map != null) {
			MultimapJoiner joiner = new MultimapJoiner(Joiner.on("\n  " + indentation), "\n" + indentation, " -> ");
			result.append("\n" + indentation).append(prefix).append(": ");
			joiner.appendTo(result, map);
		}
	}
	
}

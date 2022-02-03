/*
 * Copyright 2019-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kubernetes.client.examples.reconciler;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.util.generic.GenericKubernetesApi;

/**
 * @author Dave Syer
 *
 */
public class ChildReconciler<P extends KubernetesObject, T extends KubernetesObject, L extends KubernetesListObject>
		implements SubReconciler<P> {

	private static Log log = LogFactory.getLog(ChildReconciler.class);

	private GenericKubernetesApi<T, L> children;

	private ChildProvider<P, T> provider;

	public ChildReconciler(GenericKubernetesApi<T, L> api, ChildProvider<P, T> provider) {
		this.children = api;
		this.provider = provider;
	}

	@Override
	public Result reconcile(P parent) {
		log.info("Reconciling: " + parent.getKind() + " - " + parent.getMetadata().getName());
		List<T> items = new ArrayList<>();
		for (KubernetesObject item : children.list(parent.getMetadata().getNamespace()).getObject().getItems()) {
			if (item.getMetadata().getOwnerReferences() != null) {
				for (V1OwnerReference owner : item.getMetadata().getOwnerReferences()) {
					if (parent.getMetadata().getUid().equals(owner.getUid())) {
						@SuppressWarnings("unchecked")
						T thing = (T) item;
						items.add(thing);
						break;
					}
				}
			}
		}

		T actual = null;
		if (items.size() == 1) {
			actual = items.get(0);
		}
		else {
			for (T item : items) {
				log.info("Deleting " + item);
				children.delete(item.getMetadata().getNamespace(), item.getMetadata().getName());
			}
		}

		T desired = this.provider.desired(parent);
		if (desired == null) {
			if (actual != null) {
				log.info("Deleting " + actual);
				children.delete(actual.getMetadata().getNamespace(), actual.getMetadata().getName());
			}
			return new Result(false);
		}

		setOwner(desired, parent);
		if (actual == null) {
			try {
				actual = children.create(desired).throwsApiException().getObject();
				log.debug("Created: \n" + actual);
			}
			catch (ApiException e) {
				reflectStatusOnParent(parent, actual, e);
				throw new IllegalStateException(e);
			}
		}
		else {

			harmonizeImmutableFields(actual, desired);
			if (!semanticEquals(actual, desired)) {
				T current = actual;
				mergeBeforeUpdate(current, desired);
				children.update(current);
			}

		}

		reflectStatusOnParent(parent, actual, null);

		return new Result(false);

	}

	private void setOwner(T child, P owner) {
		var v1OwnerReference = new V1OwnerReference();
		v1OwnerReference.setKind(owner.getKind());
		v1OwnerReference.setName(owner.getMetadata().getName());
		v1OwnerReference.setBlockOwnerDeletion(true);
		v1OwnerReference.setController(true);
		v1OwnerReference.setUid(owner.getMetadata().getUid());
		v1OwnerReference.setApiVersion(owner.getApiVersion());
		child.getMetadata().addOwnerReferencesItem(v1OwnerReference);
	}

	private void reflectStatusOnParent(P parent, T actual, @Nullable ApiException e) {
		this.provider.reflectStatusOnParent(parent, actual, e);
	}

	private void mergeBeforeUpdate(T current, T desired) {
		current.getMetadata().setLabels(desired.getMetadata().getLabels());
		this.provider.mergeBeforeUpdate(current, desired);
	}

	private boolean semanticEquals(T actual, T desired) {
		if (actual == null && desired != null || desired == null && actual != null) {
			return false;
		}
		return actual != null
				&& ChildProvider.mapEquals(desired.getMetadata().getLabels(), actual.getMetadata().getLabels())
				&& this.provider.semanticEquals(actual, desired);
	}

	private void harmonizeImmutableFields(T actual, T desired) {
		this.provider.harmonizeImmutableFields(actual, desired);
	}

}

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
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Dave Syer
 *
 */
public abstract class ChildReconciler<P extends KubernetesObject, T extends KubernetesObject, L extends KubernetesListObject>
		implements SubReconciler<P> {

	protected Log logger = LogFactory.getLog(getClass());

	private GenericKubernetesApi<T, L> children;

	public ChildReconciler(GenericKubernetesApi<T, L> api) {
		this.children = api;
	}

	@Override
	public Result reconcile(P parent) {
		logger.info("Reconciling: " + parent.getKind() + " - " + parent.getMetadata().getName());
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
				logger.info("Deleting " + item);
				children.delete(item.getMetadata().getNamespace(), item.getMetadata().getName());
			}
		}

		T desired = desired(parent);
		if (desired == null) {
			if (actual != null) {
				logger.info("Deleting " + actual);
				children.delete(actual.getMetadata().getNamespace(), actual.getMetadata().getName());
			}
			return new Result(false);
		}

		V1OwnerReference v1OwnerReference = new V1OwnerReference();
		v1OwnerReference.setKind(parent.getKind());
		v1OwnerReference.setName(parent.getMetadata().getName());
		v1OwnerReference.setBlockOwnerDeletion(true);
		v1OwnerReference.setController(true);
		v1OwnerReference.setUid(parent.getMetadata().getUid());
		v1OwnerReference.setApiVersion(parent.getApiVersion());
		desired.getMetadata().addOwnerReferencesItem(v1OwnerReference);
		if (actual == null) {
			try {
				actual = children.create(desired).throwsApiException().getObject();
				logger.debug("Created: \n" + actual);
			}
			catch (ApiException e) {
				throw new IllegalStateException(e);
			}
		}
		else {

			harmonizeImmutableFields(actual, desired);
			if (!semanticEquals(desired, actual)) {
				T current = actual;
				mergeBeforeUpdate(current, desired);
				children.update(current);
			}

		}

		return new Result(false);

	}

	protected void mergeBeforeUpdate(T current, T desired) {
		current.getMetadata().setLabels(desired.getMetadata().getLabels());
	}

	protected boolean semanticEquals(T desired, T actual) {
		if (actual == null && desired != null || desired == null && actual != null) {
			return false;
		}
		return actual != null && mapEquals(desired.getMetadata().getLabels(), actual.getMetadata().getLabels());
	}

	protected void harmonizeImmutableFields(T actual, T desired) {
	}

	protected abstract T desired(P parent);

	protected static boolean mapEquals(Map<String, String> actual, Map<String, String> desired) {
		if (actual == null && desired != null) {
			return desired.isEmpty();
		}
		if (desired == null && actual != null) {
			return actual.isEmpty();
		}
		return Objects.equals(actual, desired);
	}

}

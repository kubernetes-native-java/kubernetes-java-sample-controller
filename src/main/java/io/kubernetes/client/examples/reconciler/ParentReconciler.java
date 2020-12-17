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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.kubernetes.client.apimachinery.GroupVersion;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;

import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
public class ParentReconciler<T extends KubernetesObject, L extends KubernetesListObject> implements Reconciler {

	private SharedIndexInformer<T> parentInformer;

	private SubReconciler<T>[] reconcilers;

	private CustomObjectsApi api;

	private String pluralName;

	public ParentReconciler(SharedIndexInformer<T> parentInformer, ApiClient api, List<SubReconciler<T>> reconcilers) {
		this(null, parentInformer, api, reconcilers);
	}

	public ParentReconciler(@Nullable String pluralName, SharedIndexInformer<T> parentInformer, ApiClient api,
			List<SubReconciler<T>> reconcilers) {
		this.parentInformer = parentInformer;
		this.pluralName = pluralName;
		this.api = new CustomObjectsApi(api);
		@SuppressWarnings("unchecked")
		SubReconciler<T>[] array = (SubReconciler<T>[]) reconcilers.toArray();
		this.reconcilers = array;
	}

	@Override
	public Result reconcile(Request request) {
		Lister<T> parentLister = new Lister<>(parentInformer.getIndexer(), request.getNamespace());
		T parent = parentLister.get(request.getName());

		Result result = new Result(false);
		if (parent != null) {

			if (parent.getMetadata().getDeletionTimestamp() != null) {
				return result;
			}

			for (SubReconciler<T> subReconciler : reconcilers) {
				result = aggregate(subReconciler.reconcile(parent), result);
			}

			// TODO: make this conditional on the status having changed
			try {
				GroupVersion gv = GroupVersion.parse(parent);
				String pluralName = findPluralName(parent);
				api.patchNamespacedCustomObjectStatus(gv.getGroup(), gv.getVersion(),
						parent.getMetadata().getNamespace(), pluralName, parent.getMetadata().getName(),
						Arrays.asList(new ParentPatch(extractStatus(parent))), null, null, null);
			}
			catch (ApiException e) {
				throw new IllegalStateException("Cannot update parent", e);
			}

		}

		return result;
	}

	private String findPluralName(T parent) {
		if (this.pluralName != null) {
			return this.pluralName;
		}
		return parent.getKind().toLowerCase() + "s";
	}

	private Object extractStatus(T parent) {
		Method method = ReflectionUtils.findMethod(parent.getClass(), "getStatus");
		if (method != null) {
			Object status = ReflectionUtils.invokeMethod(method, parent);
			if (status != null) {
				return status;
			}
		}
		return Collections.emptyMap();
	}

	public static class ParentPatch {

		private String op = "replace";

		private String path = "/status";

		private Object value;

		public ParentPatch(Object value) {
			this.value = value;
		}

		public String getOp() {
			return op;
		}

		public void setOp(String op) {
			this.op = op;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public Object getValue() {
			return value;
		}

		public void setValue(Object value) {
			this.value = value;
		}

	}

	private Result aggregate(Result result, Result aggregate) {
		if (aggregate.getRequeueAfter() != null
				&& (aggregate.getRequeueAfter().isZero() || result.getRequeueAfter() != null
						&& aggregate.getRequeueAfter().compareTo(result.getRequeueAfter()) > 0)) {
			aggregate.setRequeueAfter(result.getRequeueAfter());
		}
		if (result.isRequeue()) {
			aggregate.setRequeue(true);
		}
		return aggregate;
	}

}

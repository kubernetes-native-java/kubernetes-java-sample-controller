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

import java.util.List;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;

/**
 * @author Dave Syer
 *
 */
public class ParentReconciler<T extends KubernetesObject> implements Reconciler {

	private SharedIndexInformer<T> parentInformer;

	private SubReconciler<T>[] reconcilers;

	@SuppressWarnings("unchecked")
	public ParentReconciler(SharedIndexInformer<T> parentInformer, List<SubReconciler<T>> reconcilers) {
		this.parentInformer = parentInformer;
		this.reconcilers = (SubReconciler<T>[]) reconcilers.toArray();
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

		}

		return result;
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

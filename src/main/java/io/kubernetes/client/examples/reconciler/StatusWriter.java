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

import java.util.Arrays;
import java.util.function.Function;

import javax.annotation.Nullable;

import io.kubernetes.client.apimachinery.GroupVersion;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.generic.options.UpdateOptions;

/**
 * @author Dave Syer
 *
 */
public class StatusWriter {

	private CustomObjectsApi api;

	public StatusWriter(ApiClient api) {
		this.api = new CustomObjectsApi(api);
	}

	public void update(KubernetesObject object, Function<KubernetesObject, String> plural,
			Function<KubernetesObject, Object> status, @Nullable UpdateOptions options) throws ApiException {
		GroupVersion gv = GroupVersion.parse(object);
		String pluralName = plural.apply(object);
		String dryRun = options == null ? null : options.getDryRun();
		String fieldManager = options == null ? null : options.getFieldManager();
		api.patchNamespacedCustomObjectStatus(gv.getGroup(), gv.getVersion(), object.getMetadata().getNamespace(),
				pluralName, object.getMetadata().getName(), Arrays.asList(new ParentPatch(status.apply(object))),
				dryRun, fieldManager, null);

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

}

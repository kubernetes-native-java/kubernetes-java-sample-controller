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

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.UpdateOptions;
import okhttp3.Call;
import okhttp3.HttpUrl;

/**
 * @author Dave Syer
 *
 */
public class GenericStatusWriter<ApiType extends KubernetesObject> {

	private Class<ApiType> apiTypeClass;

	private String apiGroup;

	private String apiVersion;

	private String resourcePlural;

	private CustomObjectsApi customObjectsApi;

	public GenericStatusWriter(Class<ApiType> apiTypeClass, String apiGroup, String apiVersion, String resourcePlural,
			ApiClient apiClient) {
		this.apiGroup = apiGroup;
		this.apiVersion = apiVersion;
		this.resourcePlural = resourcePlural;
		this.apiTypeClass = apiTypeClass;
		this.customObjectsApi = new CustomObjectsApi(apiClient);
	}

	public KubernetesApiResponse<ApiType> updateStatus(ApiType object, Function<ApiType, Object> status,
			final UpdateOptions updateOptions) {
		V1ObjectMeta objectMeta = object.getMetadata();
		return executeCall(customObjectsApi.getApiClient(), apiTypeClass, () -> {
			//// TODO(yue9944882): judge namespaced object via api discovery
			boolean isNamespaced = !Strings.isNullOrEmpty(objectMeta.getNamespace());
			if (isNamespaced) {
				return customObjectsApi.patchNamespacedCustomObjectStatusCall(this.apiGroup, this.apiVersion,
						objectMeta.getNamespace(), this.resourcePlural, objectMeta.getName(),
						Arrays.asList(new StatusPatch(status.apply(object))), updateOptions.getDryRun(),
						updateOptions.getFieldManager(), null, null);
			}
			else {
				return customObjectsApi.patchClusterCustomObjectStatusCall(this.apiGroup, this.apiVersion,
						this.resourcePlural, objectMeta.getName(), Arrays.asList(new StatusPatch(status.apply(object))),
						updateOptions.getDryRun(), updateOptions.getFieldManager(), null, null);
			}
		});
	}

	private static <DataType extends KubernetesType> KubernetesApiResponse<DataType> getKubernetesApiResponse(
			Class<DataType> dataClass, JsonElement element, Gson gson) {
		return getKubernetesApiResponse(dataClass, element, gson, 200);
	}

	private static <DataType extends KubernetesType> KubernetesApiResponse<DataType> getKubernetesApiResponse(
			Class<DataType> dataClass, JsonElement element, Gson gson, int httpStatusCode) {
		JsonElement kindElement = element.getAsJsonObject().get("kind");
		boolean isStatus = kindElement != null && "Status".equals(kindElement.getAsString());
		if (isStatus) {
			return new KubernetesApiResponse<>(gson.fromJson(element, V1Status.class), httpStatusCode);
		}
		return new KubernetesApiResponse<>(gson.fromJson(element, dataClass));
	}

	private <DataType extends KubernetesType> KubernetesApiResponse<DataType> executeCall(ApiClient apiClient,
			Class<DataType> dataClass, CallBuilder callBuilder) {
		try {
			Call call = callBuilder.build();
			call = tweakCallForCoreV1Group(call);
			JsonElement element = apiClient.<JsonElement>execute(call, JsonElement.class).getData();
			return getKubernetesApiResponse(dataClass, element, apiClient.getJSON().getGson());
		}
		catch (ApiException e) {
			if (e.getCause() instanceof IOException) {
				throw new IllegalStateException(e.getCause()); // make this a checked
																// exception?
			}
			final V1Status status;
			try {
				status = apiClient.getJSON().deserialize(e.getResponseBody(), V1Status.class);
			}
			catch (JsonSyntaxException jsonEx) {
				// craft a status object
				return new KubernetesApiResponse<>(new V1Status().code(e.getCode()).message(e.getResponseBody()),
						e.getCode());
			}
			if (null == status) { // the response body can be something unexpected
									// sometimes..
				// this line should never reach?
				throw new RuntimeException(e);
			}
			return new KubernetesApiResponse<>(status, e.getCode());
		}
	}

	// CallBuilder builds a call and throws ApiException otherwise.
	private interface CallBuilder {

		/**
		 * Build call.
		 * @return the call
		 * @throws ApiException the api exception
		 */
		Call build() throws ApiException;

	}

	private Call tweakCallForCoreV1Group(Call call) {
		if (!apiGroup.equals("")) {
			return call;
		}
		HttpUrl url = call.request().url();
		HttpUrl tweakedUrl = url.newBuilder().removePathSegment(1).setPathSegment(0, "api").build();
		return this.customObjectsApi.getApiClient().getHttpClient()
				.newCall(call.request().newBuilder().url(tweakedUrl).build());
	}

	public static class StatusPatch {

		private String op = "replace";

		private String path = "/status";

		private Object value;

		public StatusPatch(Object value) {
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

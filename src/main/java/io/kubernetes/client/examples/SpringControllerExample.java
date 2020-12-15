/*
Copyright 2020 The Kubernetes Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.kubernetes.client.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.examples.models.V1ConfigClient;
import io.kubernetes.client.examples.models.V1ConfigClientList;
import io.kubernetes.client.examples.models.V1ConfigClientStatus;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.DefaultControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.util.generic.GenericKubernetesApi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class SpringControllerExample {

	public static void main(String[] args) {
		SpringApplication.run(SpringControllerExample.class, args);
	}

	@Configuration
	public static class AppConfig {

		@Bean
		public CommandLineRunner commandLineRunner(SharedInformerFactory sharedInformerFactory, Controller controller) {
			return args -> {
				System.out.println("starting informers..");
				sharedInformerFactory.startAllRegisteredInformers();

				System.out.println("running controller..");
				controller.run();
			};
		}

		@Bean
		public Controller nodePrintingController(SharedInformerFactory sharedInformerFactory,
				ConfigClientReconciler reconciler) {
			DefaultControllerBuilder builder = ControllerBuilder.defaultBuilder(sharedInformerFactory);
			builder = builder.watch((q) -> {
				return ControllerBuilder.controllerWatchBuilder(V1ConfigClient.class, q).withWorkQueueKeyFunc(node -> {
					System.err.println("ConfigClient: " + node.getMetadata().getName());
					return new Request(node.getMetadata().getNamespace(), node.getMetadata().getName());
				}).withResyncPeriod(Duration.ofHours(1)).build();
			});
			builder.withWorkerCount(2);
			return builder.withReconciler(reconciler).withName("configClientController").build();
		}

		@Bean
		public SharedIndexInformer<V1ConfigClient> nodeInformer(ApiClient apiClient,
				SharedInformerFactory sharedInformerFactory) {
			final GenericKubernetesApi<V1ConfigClient, V1ConfigClientList> api = new GenericKubernetesApi<>(
					V1ConfigClient.class, V1ConfigClientList.class, "spring.io", "v1", "configclients", apiClient);
			return sharedInformerFactory.sharedIndexInformerFor(api, V1ConfigClient.class, 0);
		}

	}

	@Component
	public static class ConfigClientReconciler implements Reconciler {

		@Value("${namespace}")
		private String namespace;

		private GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configmaps;

		private SharedIndexInformer<V1ConfigClient> nodeInformer;

		private GenericKubernetesApi<V1ConfigClient, V1ConfigClientList> configclients;

		public ConfigClientReconciler(SharedIndexInformer<V1ConfigClient> nodeInformer, ApiClient apiClient) {
			super();
			this.nodeInformer = nodeInformer;
			this.configmaps = new GenericKubernetesApi<>(V1ConfigMap.class, V1ConfigMapList.class, "", "v1",
					"configmaps", apiClient);
			this.configclients = new GenericKubernetesApi<>(V1ConfigClient.class, V1ConfigClientList.class, "spring.io",
					"v1", "configclients", apiClient);
		}

		@Override
		public Result reconcile(Request request) {
			Lister<V1ConfigClient> nodeLister = new Lister<>(nodeInformer.getIndexer(), request.getNamespace());

			V1ConfigClient parent = nodeLister.get(request.getName());

			if (parent != null) {

				Boolean complete = parent.getStatus() == null ? null : parent.getStatus().getComplete();

				System.out.println("reconciling " + parent.getMetadata().getName());
				List<V1ConfigMap> items = new ArrayList<>();
				for (V1ConfigMap item : configmaps.list(request.getNamespace()).getObject().getItems()) {
					System.out.println("  configmap " + item.getMetadata().getName());
					if (item.getMetadata().getOwnerReferences() != null) {
						for (V1OwnerReference owner : item.getMetadata().getOwnerReferences()) {
							if (parent.getMetadata().getUid().equals(owner.getUid())) {
								System.out.println("    owned " + owner.getName());
								items.add(item);
								break;
							}
						}
					}
				}

				V1ConfigMap actual = null;
				if (items.size() == 1) {
					actual = items.get(0);
				}
				else {
					for (V1ConfigMap item : items) {
						System.out.println("deleting " + item);
						configmaps.delete(item.getMetadata().getNamespace(), item.getMetadata().getName());
					}
				}

				V1ConfigMap desired = desired(parent);
				if (desired == null) {
					if (actual != null) {
						System.out.println("deletes " + actual);
						configmaps.delete(actual.getMetadata().getNamespace(), actual.getMetadata().getName());
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
						actual = configmaps.create(desired).throwsApiException().getObject();
						System.out.println("created " + actual);
					}
					catch (ApiException e) {
						throw new IllegalStateException(e);
					}
				}
				else {

					harmonizeImmutableFields(actual, desired);
					if (!semanticEquals(desired, actual)) {
						V1ConfigMap current = actual;
						mergeBeforeUpdate(current, desired);
						configmaps.update(current);
					}

				}

				if (complete != parent.getStatus().getComplete()) {
					configclients.update(parent).isSuccess();
				}

			}

			return new Result(false);

		}

		private void mergeBeforeUpdate(V1ConfigMap current, V1ConfigMap desired) {
			current.getMetadata().setLabels(desired.getMetadata().getLabels());
			current.setData(desired.getData());
		}

		private boolean semanticEquals(V1ConfigMap desired, V1ConfigMap actual) {
			if (actual == null && desired != null || desired == null && actual != null) {
				return false;
			}
			return actual != null && mapEquals(desired.getMetadata().getLabels(), actual.getMetadata().getLabels())
					&& mapEquals(desired.getData(), actual.getData());
		}

		private boolean mapEquals(Map<String, String> actual, Map<String, String> desired) {
			if (actual == null && desired != null) {
				return desired.isEmpty();
			}
			if (desired == null && actual != null) {
				return actual.isEmpty();
			}
			return Objects.equals(actual, desired);
		}

		private void harmonizeImmutableFields(V1ConfigMap actual, V1ConfigMap desired) {
		}

		private V1ConfigMap desired(V1ConfigClient node) {
			V1ConfigMap config = new V1ConfigMap();
			config.setApiVersion("v1");
			config.setKind("ConfigMap");
			V1ObjectMeta metadata = new V1ObjectMeta();
			metadata.setName(node.getMetadata().getName());
			metadata.setNamespace(node.getMetadata().getNamespace());
			config.setMetadata(metadata);
			Environment environment = fetchEnvironment(node);
			if (node.getStatus() == null) {
				node.setStatus(new V1ConfigClientStatus());
			}
			if (environment == null) {
				node.getStatus().setComplete(false);
			}
			else {
				config.setData(environment.toMap());
				node.getStatus().setComplete(true);
			}
			return config;
		}

		private Environment fetchEnvironment(V1ConfigClient node) {
			RestTemplate rest = new RestTemplate();
			try {
				return rest.getForObject(node.getSpec().getUrl(), Environment.class);
			}
			catch (RestClientException e) {
				e.printStackTrace();
				return null;
			}
		}

	}

}

class PropertySource {

	private String name;

	private Map<String, String> source;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, String> getSource() {
		return source;
	}

	public void setSource(Map<String, String> source) {
		this.source = source;
	}

}

class Environment {

	private PropertySource[] propertySources = new PropertySource[0];

	public PropertySource[] getPropertySources() {
		return propertySources;
	}

	public Map<String, String> toMap() {
		Map<String, String> map = new HashMap<>();
		for (int i = propertySources.length; i-- > 0;) {
			PropertySource source = propertySources[i];
			map.putAll(source.getSource());
		}
		return map;
	}

	public void setPropertySources(PropertySource[] propertySources) {
		this.propertySources = propertySources;
	}

}

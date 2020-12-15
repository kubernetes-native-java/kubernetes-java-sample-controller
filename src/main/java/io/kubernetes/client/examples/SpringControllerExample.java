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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.examples.models.V1ConfigClient;
import io.kubernetes.client.examples.models.V1ConfigClientList;
import io.kubernetes.client.examples.models.V1ConfigClientStatus;
import io.kubernetes.client.examples.reconciler.ChildReconciler;
import io.kubernetes.client.examples.reconciler.ParentReconciler;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.DefaultControllerBuilder;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.GenericKubernetesApi;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
				ParentReconciler<?> reconciler) {
			DefaultControllerBuilder builder = ControllerBuilder.defaultBuilder(sharedInformerFactory);
			builder = builder.watch((q) -> {
				return ControllerBuilder.controllerWatchBuilder(V1ConfigClient.class, q)
						.withResyncPeriod(Duration.ofHours(1)).build();
			});
			builder.withWorkerCount(2);
			return builder.withReconciler(reconciler).withName("configClientController").build();
		}

		@Bean
		public GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configMapApi(ApiClient apiClient) {
			return new GenericKubernetesApi<>(V1ConfigMap.class, V1ConfigMapList.class, "", "v1", "configmaps",
					apiClient);
		}

		@Bean
		public SharedIndexInformer<V1ConfigClient> nodeInformer(ApiClient apiClient,
				SharedInformerFactory sharedInformerFactory) {
			return sharedInformerFactory.sharedIndexInformerFor(new GenericKubernetesApi<>(V1ConfigClient.class,
					V1ConfigClientList.class, "spring.io", "v1", "configclients", apiClient), V1ConfigClient.class, 0);
		}

		@Bean
		public ParentReconciler<V1ConfigClient> configClientReconciler(
				SharedIndexInformer<V1ConfigClient> parentInformer,
				GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configClientApi) {
			return new ParentReconciler<>(parentInformer, Arrays.asList(new ConfigMapReconciler(configClientApi)));
		}

	}

	private static class ConfigMapReconciler extends ChildReconciler<V1ConfigClient, V1ConfigMap, V1ConfigMapList> {

		public ConfigMapReconciler(GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> api) {
			super(api);
		}

		@Override
		protected void mergeBeforeUpdate(V1ConfigMap current, V1ConfigMap desired) {
			super.mergeBeforeUpdate(current, desired);
			current.setData(desired.getData());
		}

		@Override
		protected boolean semanticEquals(V1ConfigMap desired, V1ConfigMap actual) {
			return super.semanticEquals(desired, actual) && mapEquals(desired.getData(), actual.getData());
		}

		@Override
		protected V1ConfigMap desired(V1ConfigClient node) {
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

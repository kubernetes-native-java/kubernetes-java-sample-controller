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

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.spring.extended.controller.annotation.GroupVersionResource;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconciler;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconcilerReadyFunc;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconcilerWatch;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesReconcilerWatches;
import io.kubernetes.client.spring.extended.controller.factory.KubernetesControllerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SpringControllerExample {

	public static void main(String[] args) {
		SpringApplication.run(SpringControllerExample.class, args);
	}

	@Configuration
	public static class AppConfig {

		@Bean
		public CommandLineRunner commandLineRunner(SharedInformerFactory sharedInformerFactory,
				@Qualifier("nodePrintingController") Controller nodePrintingController) {
			return args -> {
				System.out.println("starting informers..");
				sharedInformerFactory.startAllRegisteredInformers();

				System.out.println("running controller..");
				nodePrintingController.run();
			};
		}

		@Bean
		public KubernetesControllerFactory nodePrintingController(SharedInformerFactory sharedInformerFactory,
				NodePrintingReconciler reconciler) {
			return new KubernetesControllerFactory(sharedInformerFactory, reconciler);
		}

		@Bean
		public SharedInformerFactory sharedInformerFactory() {
			return new MySharedInformerFactory();
		}

	}

	// Defining what resources is the informer-factory actually watching.
	@KubernetesInformers({
			@KubernetesInformer(apiTypeClass = V1Endpoints.class, apiListTypeClass = V1EndpointsList.class,
					groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
							resourcePlural = "endpoints")),
			@KubernetesInformer(apiTypeClass = V1Node.class, apiListTypeClass = V1NodeList.class,
					groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
							resourcePlural = "nodes")),
			@KubernetesInformer(apiTypeClass = V1Pod.class, apiListTypeClass = V1PodList.class,
					groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
							resourcePlural = "pods")), })
	public static class MySharedInformerFactory extends SharedInformerFactory {

	}

	// fully resync every 1 minute
	@KubernetesReconciler(
			watches = @KubernetesReconcilerWatches(@KubernetesReconcilerWatch(apiTypeClass = V1Node.class)))

	@Component
	public static class NodePrintingReconciler implements Reconciler {

		@Value("${namespace}")
		private String namespace;

		@Autowired
		private SharedInformer<V1Node> nodeInformer;

		@Autowired
		private SharedInformer<V1Pod> podInformer;

		@Autowired
		private Lister<V1Node> nodeLister;

		@Autowired
		private Lister<V1Pod> podLister;

		// *OPTIONAL*
		// If you feed like hold the controller from running util some condition..
		@KubernetesReconcilerReadyFunc
		public boolean informerReady() {
			return podInformer.hasSynced() && nodeInformer.hasSynced();
		}

		@Override
		public Result reconcile(Request request) {
			V1Node node = nodeLister.get(request.getName());

			System.out.println("get all pods in namespace " + namespace);
			podLister.namespace(namespace).list().stream().map(pod -> pod.getMetadata().getName())
					.forEach(System.out::println);

			System.out.println("triggered reconciling " + node.getMetadata().getName());
			return new Result(false);
		}

	}

}

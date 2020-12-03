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

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.DefaultControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;

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
				NodePrintingReconciler reconciler) {
			DefaultControllerBuilder builder = ControllerBuilder.defaultBuilder(sharedInformerFactory);
			builder = builder.watch((q) -> {
				return ControllerBuilder.controllerWatchBuilder(V1Node.class, q).withWorkQueueKeyFunc(node -> {
					System.err.println("Node: " + node.getMetadata().getName());
					return new Request(node.getMetadata().getName());
				}).withResyncPeriod(Duration.ofHours(1)).build();
			});
			builder = builder.watch((q) -> {
				return ControllerBuilder.controllerWatchBuilder(V1Pod.class, q).withWorkQueueKeyFunc(pod -> {
					System.err.println("Pod: " + pod.getMetadata().getName());
					return new Request(pod.getMetadata().getName());
				}).withResyncPeriod(Duration.ofHours(1)).build();
			});
			builder.withReadyFunc(reconciler::informerReady);
			return builder.withReconciler(reconciler).withName("nodePrintingController").build();
		}

		@Bean
		public SharedIndexInformer<V1Node> nodeInformer(ApiClient apiClient,
				SharedInformerFactory sharedInformerFactory) {
			final GenericKubernetesApi<V1Node, V1NodeList> api = new GenericKubernetesApi<>(V1Node.class,
					V1NodeList.class, "", "v1", "nodes", apiClient);
			return sharedInformerFactory.sharedIndexInformerFor(api, V1Node.class, 0);
		}

		@Bean
		public SharedIndexInformer<V1Pod> podInformer(ApiClient apiClient,
				SharedInformerFactory sharedInformerFactory) {
			final GenericKubernetesApi<V1Pod, V1PodList> api = new GenericKubernetesApi<>(V1Pod.class, V1PodList.class,
					"", "v1", "pods", apiClient);
			return sharedInformerFactory.sharedIndexInformerFor(api, V1Pod.class, 0);
		}

		@Bean
		public Lister<V1Node> nodeLister(SharedIndexInformer<V1Node> podInformer) {
			Lister<V1Node> lister = new Lister<>(podInformer.getIndexer());
			return lister;
		}

		@Bean
		public Lister<V1Pod> podLister(SharedIndexInformer<V1Pod> podInformer) {
			Lister<V1Pod> lister = new Lister<>(podInformer.getIndexer());
			return lister;
		}

	}

	@Component
	public static class NodePrintingReconciler implements Reconciler {

		@Value("${namespace}")
		private String namespace;

		private SharedInformer<V1Node> nodeInformer;

		private SharedInformer<V1Pod> podInformer;

		private Lister<V1Node> nodeLister;

		private Lister<V1Pod> podLister;

		public NodePrintingReconciler(SharedInformer<V1Node> nodeInformer, SharedInformer<V1Pod> podInformer,
				Lister<V1Node> nodeLister, Lister<V1Pod> podLister) {
			super();
			this.nodeInformer = nodeInformer;
			this.podInformer = podInformer;
			this.nodeLister = nodeLister;
			this.podLister = podLister;
		}

		public boolean informerReady() {
			return podInformer.hasSynced() && nodeInformer.hasSynced();
		}

		@Override
		public Result reconcile(Request request) {
			V1Node node = nodeLister.get(request.getName());

			if (node != null) {

				System.out.println("get all pods in namespace " + namespace);
				podLister.namespace(namespace).list().stream().map(pod -> pod.getMetadata().getName())
						.forEach(System.out::println);

				System.out.println("triggered reconciling " + node.getMetadata().getName());

			}

			return new Result(false);
		}

	}

}

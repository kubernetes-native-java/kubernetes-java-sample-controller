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
package io.kubernetes.client.examples;

import java.time.Duration;

import io.kubernetes.client.examples.models.V1ConfigClient;
import io.kubernetes.client.examples.models.V1ConfigClientList;
import io.kubernetes.client.examples.models.V1ConfigClientSpec;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest
@Testcontainers
public class ClusterIT {

	@Autowired
	private GenericKubernetesApi<V1ConfigClient, V1ConfigClientList> configs;

	@Autowired
	private GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> maps;

	@Container
	public static GenericContainer<?> configserver = new GenericContainer<>(
			DockerImageName.parse("springcloud/configserver")).withExposedPorts(8888);

	private String name;

	@AfterEach
	public void after() {
		if (name != null) {
			configs.delete("default", name);
		}
	}

	@Test
	void createConfigClientAndCheckStatus() throws Exception {

		int before = maps.list("default").getObject().getItems().size();

		V1ConfigClient client = new V1ConfigClient();
		client.setKind("ConfigClient");
		client.setApiVersion("spring.io/v1");

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setGenerateName("config-client-");
		metadata.setNamespace("default");
		client.setMetadata(metadata);

		V1ConfigClientSpec spec = new V1ConfigClientSpec();
		spec.setUrl(
				"http://" + configserver.getHost() + ":" + configserver.getMappedPort(8888) + "/app/default/master");
		client.setSpec(spec);

		KubernetesApiResponse<V1ConfigClient> response = configs.create(client);
		assertThat(response.isSuccess());
		V1ConfigClient result = response.getObject();
		assertThat(result).isNotNull();

		Awaitility.await().atMost(Duration.ofMinutes(1)).until(
				() -> configs.get("default", result.getMetadata().getName()).getObject().getStatus().getComplete());

		assertThat(maps.list("default").getObject().getItems().size()).isEqualTo(before + 1);

	}

}

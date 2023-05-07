package com.example.sidecarinjector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.admissionreview.models.AdmissionRequest;
import io.kubernetes.client.admissionreview.models.AdmissionReview;
import io.kubernetes.client.admissionreview.models.GroupVersionKind;
import io.kubernetes.client.admissionreview.models.GroupVersionResource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@SpringBootTest
public class InjectorControllerTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(InjectorControllerTests.class);

    private final InjectorController controller;
    private final ObjectMapper objectMapper;

    @Autowired
    public InjectorControllerTests(
            final InjectorController controller,
            final ObjectMapper objectMapper
    ) {

        this.controller = controller;
        this.objectMapper = objectMapper;

    }

    @SuppressWarnings("RedundantIfStatement") //
    @Test
    public void testMutate() {
        final var uid = UUID.randomUUID().toString();
        final var mockRequest = new AdmissionRequest()
                .uid(uid)
                .resource(
                        new GroupVersionResource()
                                .group("")
                                .version("v1")
                                .resource("pods")
                )
                .kind(
                        new GroupVersionKind()
                                .group("")
                                .version("v1")
                                .kind("Pod")
                )
                ._object(this.createNginxPodManifest());
        final var mockReview = new AdmissionReview()
                .apiVersion("admission.k8s.io/v1")
                .kind("AdmissionReview")
                .request(mockRequest);
        final var mockReviewPublisher = Mono.just(mockReview);

        final var responsePublisher = this.controller.mutate(mockReviewPublisher);

        StepVerifier.create(responsePublisher)
                .expectNextMatches(response -> {

                    LOGGER.debug("response: {}", response);

                    // check response status
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        return false;
                    }

                    // check response content
                    final var responseReview = response.getBody();
                    if (responseReview == null) {
                        return false;
                    }

                    if (!Objects.equals(responseReview.getApiVersion(), "admission.k8s.io/v1")) {
                        return false;
                    }
                    if (!Objects.equals(responseReview.getKind(), "AdmissionReview")) {
                        return false;
                    }

                    final var reviewResponse = responseReview.getResponse();
                    if (reviewResponse == null) {
                        return false;
                    }

                    if (!Objects.equals(reviewResponse.getUid(), uid)) {
                        return false;
                    }
                    if (!reviewResponse.getAllowed()) {
                        return false;
                    }
                    if (!Objects.equals(reviewResponse.getPatchType(), "JSONPatch")) {
                        return false;
                    }

                    final var patchBytes = reviewResponse.getPatch();
                    if (patchBytes == null) {
                        return false;
                    }
                    final ArrayNode patches;
                    try {
                        patches = this.objectMapper.readValue(patchBytes, ArrayNode.class);
                        LOGGER.debug("patches: {}", patches);
                    } catch (IOException e) {
                        LOGGER.error("failed to parse jsonpatch as array", e);
                        return false;
                    }
                    if (patches.size() != 1) {
                        return false;
                    }
                    final var patchEntry = patches.get(0);
                    if (!patchEntry.isObject()) {
                        return false;
                    }
                    final var patchEntryObj = (ObjectNode) patchEntry;
                    if (!Objects.equals(patchEntryObj.get("op").asText(), "add")) {
                        return false;
                    }
                    if (!Objects.equals(patchEntryObj.get("path").asText(), "/spec/containers/-")) {
                        return false;
                    }
                    // check only non-null
                    if (!patchEntryObj.hasNonNull("value")) {
                        return false;
                    }
                    // TODO: check value content expected

                    return true;

                })
                .expectComplete()
                .verify();

    }

    private Map<String, Object> createNginxPodManifest() {

        final var container = new V1Container()
                .name("redis")
                .image("redis:latest")
                .imagePullPolicy("IfNotPresent");

        final var podMetadata = new V1ObjectMeta()
                .name("nginx")
                .namespace("default");
        final var podSpec = new V1PodSpec()
                .addContainersItem(container)
                .restartPolicy("Always");

        final var pod = new V1Pod()
                .apiVersion("v1")
                .kind("Pod")
                .metadata(podMetadata)
                .spec(podSpec);

        return this.objectMapper.convertValue(pod, new TypeReference<>() {});

    }

}

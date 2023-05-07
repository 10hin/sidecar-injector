package com.example.sidecarinjector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kubernetes.client.admissionreview.models.AdmissionResponse;
import io.kubernetes.client.admissionreview.models.AdmissionReview;
import io.kubernetes.client.admissionreview.models.GroupVersionResource;
import io.kubernetes.client.openapi.models.V1Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/")
public class InjectorController {

    private final static Logger LOGGER = LoggerFactory.getLogger(InjectorController.class);

    private static final GroupVersionResource POD_RESOURCE = new GroupVersionResource();
    static {
        POD_RESOURCE.setGroup("");
        POD_RESOURCE.setVersion("v1");
        POD_RESOURCE.setResource("pods");
    }

    private final static String ADMISSIONREVIEW_API_VERSION = "admission.k8s.io/v1";
    private final static String ADMISSIONREVIEW_KIND = "AdmissionReview";

    private final ObjectMapper objectMapper;
    private final JsonNodeFactory jsonNodeFactory;

    @Autowired
    public InjectorController(
            final ObjectMapper objectMapper
    ) {

        this.objectMapper = objectMapper;
        this.jsonNodeFactory = this.objectMapper.getNodeFactory();

    }

    @PostMapping("")
    public Mono<ResponseEntity<AdmissionReview>> mutate(
            @RequestBody
            final Mono<AdmissionReview> reviewRequestPublisher
    ) {
        return reviewRequestPublisher
                .<AdmissionReview>handle((reviewRequest, sink) -> {
                    final var request = reviewRequest.getRequest();
                    if (request == null) {
                        final var ex = new RequestProblemException(new NullPointerException("request field is null"));
                        LOGGER.error("invalid review request body", ex);
                        sink.error(ex);
                        return;
                    }

                    final var resource = request.getResource();
                    if (resource == null) {
                        final var ex = new RequestProblemException(new NullPointerException("request.resource field is null"));
                        LOGGER.error("invalid review request body", ex);
                        sink.error(ex);
                        return;
                    }
                    if (!POD_RESOURCE.equals(resource)) {
                        final var ex = new RequestProblemException(new IllegalArgumentException("request.resource value not core/v1/pods"));
                        LOGGER.error("invalid review request body", ex);
                        sink.error(ex);
                        return;
                    }

                    final var response = new AdmissionResponse();
                    final var uid = request.getUid();
                    if (uid == null) {
                        final var ex = new RequestProblemException(new NullPointerException("request.uid field is null"));
                        LOGGER.error("invalid review request body", ex);
                        sink.error(ex);
                        return;
                    }

                    response.setUid(uid);
                    response.setAllowed(true);
                    response.setPatchType("JSONPatch");
                    response.setPatch(createPatch());

                    final var reviewResponse = new AdmissionReview()
                            .apiVersion(ADMISSIONREVIEW_API_VERSION)
                            .kind(ADMISSIONREVIEW_KIND)
                            .response(response);

                    sink.next(reviewResponse);
                })
                .map(ResponseEntity::ok)
                .onErrorResume(
                        RequestProblemException.class,
                        (throwableNotUsed) -> Mono.just(ResponseEntity.badRequest().build())
                );
    }

    private static class RequestProblemException extends RuntimeException {
        public RequestProblemException(Throwable cause) {
            super(cause);
        }
    }

    private byte[] createPatch() {
        final var container = new V1Container();
        container.setName("sidecar-nginx");
        container.setImage("nginx:1.21-alpine");
        container.setImagePullPolicy("IfNotPresent");

        final var addContainerOperation = this.jsonNodeFactory.objectNode()
                .put("op", "add")
                .put("path", "/spec/containers/-")
                .putPOJO("value", container);

        final var patchArray = this.jsonNodeFactory.arrayNode(1)
                .add(addContainerOperation);

        try {
            return this.objectMapper.writeValueAsBytes(patchArray);
        } catch (JsonProcessingException e) {
            throw new InternalError("failed to serialize (format) to JSON", e);
        }
    }

}

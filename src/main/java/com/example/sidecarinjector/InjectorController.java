package com.example.sidecarinjector;

import io.kubernetes.client.admissionreview.models.AdmissionResponse;
import io.kubernetes.client.admissionreview.models.AdmissionReview;
import io.kubernetes.client.admissionreview.models.GroupVersionResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


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
                    final var patch = "[{\"op\":\"add\",\"path\":\"/spec/containers/-\",\"value\":{\"name\":\"sidecar-nginx\",\"image\":\"nginx:1.21-alpine\",\"imagePullPolicy\":\"IfNotPresent\"}}]";
                    response.setPatch(patch.getBytes(StandardCharsets.UTF_8));

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

}

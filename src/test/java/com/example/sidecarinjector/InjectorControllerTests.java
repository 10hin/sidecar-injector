package com.example.sidecarinjector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class InjectorControllerTests {

    private final InjectorController controller;

    @Autowired
    public InjectorControllerTests(
            final InjectorController controller
    ) {

        this.controller = controller;

    }

}

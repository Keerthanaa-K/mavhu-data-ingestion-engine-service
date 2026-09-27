package com.mavhu.ingestion.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ingestion/v1")
public class LivenessCheck {

    @GetMapping("/alive")
    public String alive() {
        log.info("Received request for alive endpoint");
        return "alive";
    }
}

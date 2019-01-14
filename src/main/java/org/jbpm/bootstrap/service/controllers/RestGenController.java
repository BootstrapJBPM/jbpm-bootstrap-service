package org.jbpm.bootstrap.service.controllers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jbpm.bootstrap.model.Project;
import org.jbpm.bootstrap.service.util.BuildComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RestGenController {

    private static final Logger logger = LoggerFactory.getLogger(RestGenController.class);

    @Autowired
    BuildComponent buildComponent;

    @PostMapping(value = "/gen",
            produces = {"application/octet-stream"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> buildApp(@RequestBody Map<String, Object> body) throws Exception {

        Project project = new Project();
        List<String> options = (List) body.get("options");
        String capabilities = (String) body.get("capabilities");
        String name = (String) body.get("name");
        String packageName = (String) body.get("packagename");
        String version = (String) body.get("version");

        if (options != null) {
            project.setOptions(options);
        }

        if (capabilities != null && capabilities.length() > 0) {
            project.setCapabilities(Arrays.asList(capabilities.split("\\s*,\\s*")));
        }

        if (name != null && name.length() > 0) {
            project.setName(name);
        }

        if (packageName != null && packageName.length() > 0) {
            project.setPackageName(packageName);
        }

        if (version != null && version.length() > 0) {
            project.setVersion(version);
        }

        logger.info("Received request for generating application for project {}",
                    project);

        return buildComponent.buildApp(project,
                                       false);
    }
}

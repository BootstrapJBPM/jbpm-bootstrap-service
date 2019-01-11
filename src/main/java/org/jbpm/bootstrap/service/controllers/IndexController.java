/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.bootstrap.service.controllers;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.jbpm.bootstrap.model.Project;
import org.jbpm.bootstrap.service.util.BuildComponent;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.admin.ProcessInstanceAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class IndexController {

    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);

    @Autowired
    private ProcessService processService;

    @Autowired
    ProcessInstanceAdminService processInstanceAdminService;

    @Autowired
    BuildComponent buildComponent;

    @GetMapping("/")
    public String showIndex(Model model) {
        return "index";
    }

    @GetMapping("/reports")
    public String showReports(Model model) {
        return "reports";
    }

    @ModelAttribute("project")
    public Project getProject() {
        return new Project();
    }

    @PostMapping(value = "/", produces = {"application/octet-stream"})
    public @ResponseBody
    ResponseEntity<?> buildApp(@ModelAttribute Project project) throws Exception {
        logger.info("Received request for generating application for project {}",
                    project);

        if (project == null) {
            logger.error("Project is missing");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return buildComponent.buildApp(project, true);
    }

    @GetMapping("/generatingmodal")
    public String getGeneratingModal() {
        return "fragments :: generatingmodal";
    }

    @ExceptionHandler(Exception.class)
    public String exception(final Exception e,
                            final Model model) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        model.addAttribute("stacktrace",
                           errors.toString());
        String errorMessage = (e != null ? e.getMessage() : "Unknown error");
        model.addAttribute("errorMessage",
                           errorMessage);
        return "error";
    }
}

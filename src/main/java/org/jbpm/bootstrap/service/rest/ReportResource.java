package org.jbpm.bootstrap.service.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jbpm.kie.services.impl.query.mapper.RawListQueryMapper;
import org.jbpm.services.api.query.QueryService;
import org.jbpm.services.api.query.model.QueryParam;
import org.kie.api.runtime.query.QueryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

@Path("reports")
public class ReportResource {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportResource.class);

    private ObjectMapper mapper = new ObjectMapper();
    
    @Autowired
    private QueryService queryService;
    
    @GET
    @Path("stats")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response collectStats() {
        try {
            List<Number> collectedStatistics = new ArrayList<>();
            
            List<List<Object>> totalProcesses = queryService.query("jbpmBootstrapProcessInstances", 
                    RawListQueryMapper.get(), 
                    new QueryContext(), 
                    QueryParam.count("processInstanceId"));
            
            List<List<Object>> todayProcesses = queryService.query("jbpmBootstrapProcessInstances", 
                    RawListQueryMapper.get(), 
                    new QueryContext(), 
                    QueryParam.equalsTo("start_date", LocalDate.now().toString()),
                    QueryParam.count("processInstanceId"));
            
            List<List<Object>> totalErrors = queryService.query("jbpmBootstrapExecutionErrorList", 
                    RawListQueryMapper.get(), 
                    new QueryContext(), 
                    QueryParam.count("ERROR_ID"));

            collectedStatistics.add((Number)totalProcesses.get(0).get(0));
            collectedStatistics.add((Number)todayProcesses.get(0).get(0));
            collectedStatistics.add((Number)totalErrors.get(0).get(0));
            
            return Response.ok()
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapper.writeValueAsString(collectedStatistics))
                .build();
        } catch (Exception e) {
            logger.error("Unexepcted error while collecting statistics", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
    
    @GET
    @Path("types")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response collectByTypes() {
        try {
            
            QueryParam[] parameters = QueryParam.getBuilder()
                    .append(QueryParam.groupBy("value"))
                    .append(QueryParam.count("processInstanceId"))
                    .append(QueryParam.equalsTo("variableId", "projectSetup"))
                    .get();
            
            List<List<Object>> byType = queryService.query("jbpmBootstrapProcessInstancesByVar", 
                    RawListQueryMapper.get(), 
                    new QueryContext(0, 1000), 
                    parameters); 
            
            Map<String, Number> collectedTypes = new HashMap<>();
            for (List<Object> rows : byType) {
                collectedTypes.put((String) rows.get(0), (Number) rows.get(1));            
            }
            
            collectedTypes.putIfAbsent("bpm", 0);
            collectedTypes.putIfAbsent("brm", 0);
            collectedTypes.putIfAbsent("planner", 0);
            
            return Response.ok()
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapper.writeValueAsString(collectedTypes))
                .build();
        } catch (Exception e) {
            logger.error("Unexepcted error while collecting report by type", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
    
    @GET
    @Path("apps")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response collectByDate() {
        try {
            
            QueryParam[] parameters = QueryParam.getBuilder()
                    .append(QueryParam.groupBy("end_date", QueryParam.DAY, 365))
                    .append(QueryParam.count("processInstanceId"))                    
                    .get();
            
            List<List<Object>> lastTenDays = queryService.query("jbpmBootstrapProcessInstances", 
                    RawListQueryMapper.get(), 
                    new QueryContext(0, 10, "end_date", false), 
                    parameters);                    
            
            List<Object> collectedApplications = new ArrayList<>();            
            List<Object> dates = new ArrayList<>();
           
            Collections.reverse(lastTenDays);
            
            for (List<Object> rows : lastTenDays) {
                dates.add(rows.get(0));
                collectedApplications.add(rows.get(1));
            }
            
            Map<String, List<?>> data = new HashMap<>();
            data.put("apps", collectedApplications);
            data.put("dates", dates);
            
            return Response.ok()
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapper.writeValueAsString(data))
                .build();
        } catch (Exception e) {
            logger.error("Unexepcted error while collecting report by date", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
    
    @GET
    @Path("versions")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response collectByVersion() {
        try {
            
            QueryParam[] parameters = QueryParam.getBuilder()
                    .append(QueryParam.groupBy("value"))
                    .append(QueryParam.count("processInstanceId"))
                    .append(QueryParam.equalsTo("variableId", "projectVersion"))
                    .get();
            
            List<List<Object>> byVersion = queryService.query("jbpmBootstrapProcessInstancesByVar", 
                    RawListQueryMapper.get(), 
                    new QueryContext(0, 1000), 
                    parameters); 
            
            return Response.ok()
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapper.writeValueAsString(byVersion))
                .build();
        } catch (Exception e) {
            logger.error("Unexepcted error while collecting report by version", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
    
    @GET
    @Path("options")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response collectByOption() {
        try {
            
            QueryParam[] parameters = QueryParam.getBuilder()
                    .append(QueryParam.groupBy("value"))
                    .append(QueryParam.count("processInstanceId"))
                    .append(QueryParam.equalsTo("variableId", "projectOptions"))
                    .get();
            
            List<List<Object>> byOptions = queryService.query("jbpmBootstrapProcessInstancesByVar", 
                    RawListQueryMapper.get(), 
                    new QueryContext(0, 1000), 
                    parameters);            
            
            return Response.ok()
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(mapper.writeValueAsString(byOptions))
                .build();
        } catch (Exception e) {
            logger.error("Unexepcted error while collecting report by option", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("gentypes")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response collectByGenerationType() {
        try {

            QueryParam[] parameters = QueryParam.getBuilder()
                    .append(QueryParam.groupBy("value"))
                    .append(QueryParam.count("processInstanceId"))
                    .append(QueryParam.equalsTo("variableId", "generationType"))
                    .get();


            List<List<Object>> totalProcesses = queryService.query("jbpmBootstrapProcessInstances",
                                                                   RawListQueryMapper.get(),
                                                                   new QueryContext(),
                                                                   QueryParam.count("processInstanceId"));

            List<List<Object>> byGenerationType = queryService.query("jbpmBootstrapProcessInstancesByVar",
                                                              RawListQueryMapper.get(),
                                                              new QueryContext(0, 1000),
                                                              parameters);

            List<Object> totalList = new ArrayList<>();
            totalList.add("total");
            totalList.add(totalProcesses.get(0).get(0));
            byGenerationType.add(totalList);

            return Response.ok()
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(mapper.writeValueAsString(byGenerationType))
                    .build();
        } catch (Exception e) {
            logger.error("Unexepcted error while collecting report by generation type", e);
            return Response.serverError().entity(e.getMessage()).build();
        }
    }
}

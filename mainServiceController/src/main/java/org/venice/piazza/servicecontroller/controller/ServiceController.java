/*******************************************************************************
 * Copyright 2016, RadiantBlue Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.venice.piazza.servicecontroller.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.venice.piazza.servicecontroller.data.mongodb.accessors.MongoAccessor;
import org.venice.piazza.servicecontroller.messaging.handlers.DeleteServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.DescribeServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.ExecuteServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.ListServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.RegisterServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.SearchServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.UpdateServiceHandler;
import model.data.DataType;
import model.job.type.RegisterServiceJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.SuccessResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.response.ServiceListResponse;
import model.response.ServiceResponse;
import model.service.SearchCriteria;
import model.service.metadata.ExecuteServiceData;
import model.service.metadata.Service;
import util.PiazzaLogger;

/**
 * Purpose of this controller is to handle service requests for registerin and
 * managing services.
 * 
 * @author mlynum & Sonny.Saniev
 * @since 1.0
 */

@RestController
@RequestMapping({ "/servicecontroller", "" })
public class ServiceController {

	@Autowired
	private DeleteServiceHandler dlHandler;

	@Autowired
	private UpdateServiceHandler usHandler;
	
	@Autowired
	private DescribeServiceHandler dsHandler;

	@Autowired
	private ExecuteServiceHandler esHandler;

	@Autowired
	private ListServiceHandler lsHandler;
	
	@Autowired
	private MongoAccessor accessor;

	@Autowired
	private PiazzaLogger logger;

	@Autowired
	private RegisterServiceHandler rsHandler;
	
	@Autowired
	private SearchServiceHandler ssHandler;
	
	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	
     /**
      * Empty controller for now
      */
	public ServiceController() {

	}

	/**
	 * Registers a service with the piazza service controller.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Service/post_service"
	 * 
	 *      This service is meant for internal Piazza use, Swiss-Army-Knife
	 *      (SAK) administration and for testing of the serviceController.
	 * @param serviceMetadata
	 *            metadata about the service
	 * @return A Json message with the resourceID {resourceId="<the id>"}
	 */
	@RequestMapping(value = "/registerService", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public PiazzaResponse registerService(@RequestBody PiazzaJobRequest jobRequest) {
		try {
			RegisterServiceJob serviceJob = (RegisterServiceJob) jobRequest.jobType;
			String serviceId = rsHandler.handle(serviceJob.data);
			return new ServiceResponse(serviceId);
		} catch (Exception exception) {			
			logger.log(exception.toString(), PiazzaLogger.ERROR);
			return new ErrorResponse("unknown", String.format("Error Registering Service: %s", exception.getMessage()),
					"Service Controller");
		}
	}

	/**
	 * Gets service metadata, based on its ID.
	 * 
	 * @see "@see "
	 *      http://pz-swagger.stage.geointservices.io/#!/Service/post_service"
	 * 
	 * @param serviceId
	 *            The ID of the service.
	 * @return The service metadata or appropriate error
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.GET)
	public PiazzaResponse getServiceInfo(@PathVariable(value = "serviceId") String serviceId) {
		try {
			Service service = accessor.getServiceById(serviceId);
			return new ServiceResponse(service);
		} catch (Exception exception) {
			logger.log(exception.toString(), PiazzaLogger.ERROR);
			return new ErrorResponse(null, String.format("Could not look up Service %s information: %s", serviceId, exception.getMessage()),
					"Service Controller");
		}
	}

	/**
	 * Gets the list of services currently registered.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Service/get_service"
	 * 
	 * @return The list of registered services.
	 */
	@RequestMapping(value = "/service", method = RequestMethod.GET)
	public PiazzaResponse getServices(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "userName", required = false) String userName) {
		try {
			Pattern regex = Pattern.compile(String.format("(?i)%s", keyword != null ? keyword : ""));
			
			// Get a DB Cursor to the query for general data
			DBCursor<Service> cursor = accessor
					.getServiceCollection()
					.find()
					.or(DBQuery.regex("resourceMetadata.name", regex),
							DBQuery.regex("resourceMetadata.description", regex), DBQuery.regex("url", regex),
							DBQuery.regex("serviceId", regex));
			if ((userName != null) && !(userName.isEmpty())) {
				cursor.and(DBQuery.is("resourceMetadata.createdBy", userName));
			}
			Integer size = new Integer(cursor.size());

			// Filter the data by pages
			List<Service> data = cursor.skip(page * pageSize).limit(pageSize).toArray();

			// Attach pagination information
			Pagination pagination = new Pagination(size, page, pageSize);

			// Create the Response and send back
			return new ServiceListResponse(data, pagination);
		} catch (Exception exception) {
			String error = String.format("Error Listing Services: %s", exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ErrorResponse(null, error, "Service Controller");
		}
	}

	/**
	 * Deletes a registered service.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Service/delete_service_serviceId"
	 * 
	 * @param serviceId
	 *            The ID of the service to delete.
	 * @return Null if service is deleted without error, or error if an
	 *         exception occurs..
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.DELETE)
	public PiazzaResponse unregisterService(@PathVariable(value = "serviceId") String serviceId, @RequestParam(value = "softDelete", required = false) boolean softDelete) {
		try {
			// remove from elastic search as well....
			dlHandler.handle(serviceId, softDelete);
			return new SuccessResponse(null, "Service was deleted successfully.", "ServiceController");
		} catch (Exception exception) {
			String error = String.format("Error Deleting service %s: %s", serviceId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ErrorResponse(null, error, "Service Controller");
		}
	}
	
	/**
	 * Updates a service with new Metadata.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Service/put_service_serviceId"
	 * 
	 * @param serviceId
	 *            Service ID to delete.
	 * @param serviceData
	 *            The data of the service to update.
	 * @return Null if the service has been updated, or an appropriate error if
	 *         there is one.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.PUT)
	public PiazzaResponse updateServiceMetadata(@PathVariable(value = "serviceId") String serviceId, @RequestBody Service serviceData) {
		try {
			if (serviceId.equalsIgnoreCase(serviceData.getServiceId())) {
				String result = usHandler.handle(serviceData);
				if (result.length() > 0) {
					return new SuccessResponse(null, "Service was updated successfully.", "ServiceController");
				} else {
					return new ErrorResponse(null, "The update for serviceId " + serviceId + " did not happen successfully",
							"Service Controller");
				}
			} else {
				return new ErrorResponse(null,
						String.format("Cannot Update Service because the Metadata ID (%s) does not match the Specified ID (%s)",
								serviceData.getServiceId(), serviceId),
						"Service Controller");
			}
		} catch (Exception exception) {
			String error = String.format("Error Updating service %s: %s", serviceId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ErrorResponse(null, error, "Service Controller");
		}
	}

	/**
	 * Updates metadata about an existing service registered in the
	 * ServiceController.
	 * 
	 * This service is meant for internal Piazza use, Swiss-Army-Knife (SAK)
	 * administration and for testing of the serviceController.
	 * 
	 * @param serviceMetadata
	 *            metadata bout the service
	 * @return A Json message with the resourceID {resourceId="<the id>"}
	 */
	@RequestMapping(value = "/updateService", method = RequestMethod.PUT, headers = "Accept=application/json", produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody String updateService(@RequestBody Service serviceMetadata) {

		String result = usHandler.handle(serviceMetadata);
		logger.log("ServiceController: Result is" + "{\"resourceId:" + "\"" + result + "\"}", PiazzaLogger.DEBUG);
		String responseString = "{\"resourceId\":" + "\"" + result + "\"}";

		return responseString;
	}

	/**
	 * Executes a service registered in the Service Controller.
	 * This service is meant for internal Piazza use, Swiss-Army-Knife (SAK)
	 * administration and for testing of the serviceController.
	 * 
	 * @param data
	 *            ExecuteServiceData used to execute the data. Contains
	 *            resourceId and values to use.
	 * 
	 * @return the results of the service execution
	 */
	@RequestMapping(value = "/executeService", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> executeService(@RequestBody ExecuteServiceData data) {
		for (Map.Entry<String, DataType> entry : data.dataInputs.entrySet()) {
			String key = entry.getKey();
			logger.log("dataInput key:" + key, PiazzaLogger.DEBUG);
			logger.log("dataInput Type:" + entry.getValue().getType(), PiazzaLogger.DEBUG);
		}
		ResponseEntity<String> result = null;
		try {
			result = esHandler.handle(data);
		} catch (Exception ex) {
			logger.log("Service Controller Error Caused Exception: " + ex.toString(), PiazzaLogger.ERROR);
		}
		logger.log("Result is " + result, PiazzaLogger.DEBUG);

		// Set the response based on the service retrieved
		return result;
	}

	/**
	 * Used to describe details about the service.
	 * 
	 * This service is meant for internal Piazza use, Swiss-Army-Knife (SAK)
	 * administration and for testing of the serviceController.
	 * 
	 * @param resourceId
	 *            The id associated with the service that is registered within
	 *            the Service Controller.
	 * @return Json with the ResourceMetadata, the metadata about the service
	 */
	@RequestMapping(value = "/describeService", method = RequestMethod.GET, headers = "Accept=application/json")
	public ResponseEntity<String> describeService(@ModelAttribute("resourceId") String resourceId) {

		ResponseEntity<String> result = dsHandler.handle(resourceId);
		logger.log("Result is " + result, PiazzaLogger.DEBUG);
		// Set the response based on the service retrieved
		return result;
	}

	/**
	 * deletes a registered service from the ServiceController.
	 * 
	 * This service is meant for internal Piazza use, Swiss-Army-Knife (SAK)
	 * administration and for testing of the serviceController.
	 * 
	 * @param resourceId
	 * @return the result of the deletion
	 */
	@RequestMapping(value = "/deleteService", method = RequestMethod.GET, headers = "Accept=application/json")
	public ResponseEntity<String> deleteService(@ModelAttribute("resourceId") String resourceId) {
		logger.log("deleteService resourceId=" + resourceId, PiazzaLogger.INFO);
		String result = dlHandler.handle(resourceId, false);
		logger.log("Result is " + result, PiazzaLogger.DEBUG);
		return new ResponseEntity<String>(result, HttpStatus.OK);
	}

	/**
	 * Lists all the services registered in the service controller.
	 * 
	 * This service is meant for internal Piazza use, Swiss-Army-Knife (SAK)
	 * administration and for testing of the serviceController.
	 * 
	 * @return Json list o resourceMetadata items (Metadata about the service)
	 */
	@RequestMapping(value = "/listService", method = RequestMethod.GET, headers = "Accept=application/json")
	public ResponseEntity<String> listService() {
		logger.log("listService", PiazzaLogger.INFO);
		ResponseEntity<String> result = lsHandler.handle();
		logger.log("Result is " + result, PiazzaLogger.DEBUG);
		return result;
	}

	/**
	 * Searches for registered services. This service is meant for internal
	 * Piazza use, Swiss-Army-Knife (SAK) administration and for testing of the
	 * serviceController.
	 * 
	 * @param SearchCriteria
	 *            The criteria to search with (specify field and regular
	 *            expression
	 * 
	 * @return Json list o resourceMetadata items (Metadata about the service)
	 */
	@RequestMapping(value = "/search", method = RequestMethod.POST, headers = "Accept=application/json")
	public ResponseEntity<String> search(@RequestBody SearchCriteria criteria) {

		logger.log("search " + " " + criteria.field + "->" + criteria.pattern, PiazzaLogger.INFO);
		ResponseEntity<String> result = ssHandler.handle(criteria);
		logger.log("Result is " + result, PiazzaLogger.DEBUG);
		return result;
	}

	/**
	 * Healthcheck to see if the Piazza Service Controller is up and running.
	 * This service is meant for internal Piazza use, Swiss-Army-Knife (SAK)
	 * administration and for testing of the serviceController.
	 * 
	 * @return welcome message
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public ResponseEntity<String> healthCheck() {
		logger.log("Health Check called", PiazzaLogger.DEBUG);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.valueOf("text/html"));
		String htmlMessage = "<HTML><TITLE>Piazza Service Controller Welcome</TITLE>";
		htmlMessage = htmlMessage + "<BODY><BR> Welcome from the Piazza Service Controller. "
				+ "<BR>For details on running and using the ServiceController, "
				+ "<BR>see <A HREF=\"https://github.com/venicegeo/venice/wiki/Pz-ServiceController\"> Pz Service Controller<A> for details."
				+ "<BODY></HTML>";

		ResponseEntity<String> response = new ResponseEntity<String>(htmlMessage, responseHeaders, HttpStatus.OK);

		return response;
	}

	/**
	 * Statistics for the Piazza Service controller This service is meant for
	 * internal Piazza use, Swiss-Army-Knife (SAK) administration and for
	 * testing of the serviceController.
	 * 
	 * @return json as statistics
	 */
	@RequestMapping(value = "/admin/stats", method = RequestMethod.GET)
	public void stats(HttpServletResponse response) throws IOException {
		response.sendRedirect("/metrics");
	}
}

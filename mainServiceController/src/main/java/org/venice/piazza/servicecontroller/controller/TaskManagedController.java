/**
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
 **/
package org.venice.piazza.servicecontroller.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.venice.piazza.servicecontroller.data.mongodb.accessors.MongoAccessor;
import org.venice.piazza.servicecontroller.taskmanaged.ServiceTaskManager;

import model.job.type.ExecuteServiceJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.ServiceJobResponse;
import model.response.SuccessResponse;
import model.status.StatusUpdate;
import util.PiazzaLogger;

/**
 * REST Controller for Task-Managed Service endpoints. This includes pulling Jobs off the queue, and updating Status for
 * jobs. Also metrics such as queue length are available.
 * 
 * @author Patrick.Doody
 *
 */
@RestController
public class TaskManagedController {
	@Autowired
	private PiazzaLogger piazzaLogger;
	@Autowired
	private ServiceTaskManager serviceTaskManager;
	@Autowired
	private MongoAccessor mongoAccessor;

	private final static Logger LOGGER = LoggerFactory.getLogger(ServiceController.class);

	/**
	 * Pulls the next job off of the Service Queue.
	 * 
	 * @param username
	 *            The name of the user. Used for verification.
	 * @param serviceId
	 *            The ID of the Service
	 * @return The information for the next Job, if one is present.
	 */
	public ResponseEntity<PiazzaResponse> getNextServiceJobFromQueue(String username, String serviceId) {
		try {
			// Log the Request
			piazzaLogger.log(String.format("User %s Requesting to perform Work on Next Job for %s Service Queue.", username, serviceId),
					Severity.INFORMATIONAL);
			// Get the Job. This will mark the Job as being processed.
			ExecuteServiceJob serviceJob = serviceTaskManager.getNextJobFromQueue(serviceId);
			// Return
			return new ResponseEntity<>(new ServiceJobResponse(serviceJob), HttpStatus.OK);
		} catch (Exception exception) {
			String error = String.format("Error Getting next Service Job for Service %s by User %s: %s", serviceId, username,
					exception.getMessage());
			LOGGER.error(error, exception);
			piazzaLogger.log(error, Severity.ERROR, new AuditElement(username, "errorGettingServiceJob", serviceId));
			return new ResponseEntity<>(new ErrorResponse(error, "ServiceController"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates the Status for a Piazza Job.
	 * 
	 * @param username
	 *            The name of the user. Used for verification.
	 * @param serviceId
	 *            The ID of the Service containing the Job
	 * @param jobId
	 *            The ID of the Job to update
	 * @param statusUpdate
	 *            The update contents, including status, percentage, and possibly results.
	 * @return Success or error.
	 */
	public ResponseEntity<PiazzaResponse> updateServiceJobStatus(String username, String serviceId, String jobId,
			StatusUpdate statusUpdate) {
		try {
			// Log the Request
			piazzaLogger.log(String.format("User %s Requesting to Update Job Status for Job %s for Task-Managed Service.", username, jobId),
					Severity.INFORMATIONAL);
			// Process the Update
			serviceTaskManager.processStatusUpdate(serviceId, jobId, statusUpdate);
			// Return Success
			return new ResponseEntity<>(new SuccessResponse("OK", "ServiceController"), HttpStatus.OK);
		} catch (Exception exception) {
			String error = String.format("Could not Update status for Job %s for Service %s : %s", jobId, serviceId,
					exception.getMessage());
			LOGGER.error(error, exception);
			piazzaLogger.log(error, Severity.ERROR, new AuditElement(username, "failedToUpdateServiceJob", jobId));
			return new ResponseEntity<>(new ErrorResponse(error, "ServiceController"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets metadata for a specific Task-Managed Service.
	 * 
	 * @param username
	 *            The name of the user. Used for verification.
	 * @param serviceId
	 *            The ID of the Service
	 * @return Map containing information regarding the Task-Managed Service
	 */
	public ResponseEntity<Map<String, Object>> getServiceQueueData(String username, String serviceId) {
		try {
			// Log the Request
			piazzaLogger.log(String.format("User %s Requesting Task-Managed Service Information for Service %s", username, serviceId),
					Severity.INFORMATIONAL);
			// Fill Map with Metadata
			Map<String, Object> response = mongoAccessor.getServiceQueueCollectionMetadata(serviceId);
			// Respond
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception exception) {
			String error = String.format("Could not retrieve Service Queue data for %s : %s", serviceId, exception.getMessage());
			LOGGER.error(error, exception);
			piazzaLogger.log(error, Severity.ERROR, new AuditElement(username, "failedToRetrieveServiceQueueMetadata", serviceId));
			Map<String, Object> response = new HashMap<>();
			response.put("message", error);
			return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}

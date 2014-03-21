package org.owasp.appsensor.analysis;

import java.util.ArrayList;
import java.util.Collection;

import org.owasp.appsensor.AppSensorServer;
import org.owasp.appsensor.Attack;
import org.owasp.appsensor.DetectionPoint;
import org.owasp.appsensor.Interval;
import org.owasp.appsensor.Response;
import org.owasp.appsensor.criteria.SearchCriteria;
import org.owasp.appsensor.listener.AttackListener;
import org.owasp.appsensor.logging.Logger;
import org.owasp.appsensor.storage.ResponseStore;

/**
 * This is the reference {@link Attack} analysis engine, and is an implementation of the Observer pattern. 
 * 
 * It is notified with implementations of the {@link java.util.Observable} interface and is 
 * passed the observed object. In this case, we are only concerned with {@link Attack}
 * implementations. 
 * 
 * The implementation performs a simple analysis that checks the created attack against any created {@link Response}s. 
 * It then creates a {@link Response} and adds it to the {@link ResponseStore}. 
 * 
 * @see java.util.Observer
 *
 * @author John Melton (jtmelton@gmail.com) http://www.jtmelton.com/
 */
public class ReferenceAttackAnalysisEngine implements AnalysisEngine, AttackListener {

	private static Logger logger = AppSensorServer.getInstance().getLogger().setLoggerClass(ReferenceAttackAnalysisEngine.class);

	/**
	 * This method analyzes {@link Attack} objects that are added 
	 * to the system (either via direct addition or generated by the event analysis
	 * engine), generates an appropriate {@link Response} object, 
	 * and adds it to the configured {@link ResponseStore} 
	 * 
	 * @param observable object that was being obeserved - ignored in this case
	 * @param observedObject object that was added to observable. In this case
	 * 			we are only interested if the object is 
	 * 			an {@link Attack} object
	 */
	@Override
	public void onAdd(Attack attack) {
//		if (observedObject instanceof Attack) {
//			Attack attack = (Attack)observedObject;
			
			Response response = findAppropriateResponse(attack);
			
			if (response != null) {
				logger.info("Response set for user <" + attack.getUser().getUsername() + "> - storing response action " + response.getAction());
				AppSensorServer.getInstance().getResponseStore().addResponse(response);
			}
//		} 
	}
//	public void update(Observable observable, Object observedObject) {
//		if (observedObject instanceof Attack) {
//			Attack attack = (Attack)observedObject;
//			
//			Response response = findAppropriateResponse(attack);
//			
//			if (response != null) {
//				logger.info("Response set for user <" + attack.getUser().getUsername() + "> - storing response action " + response.getAction());
//				AppSensorServer.getInstance().getResponseStore().addResponse(response);
//			}
//		} 
//	}
	
	/**
	 * Find/generate {@link Response} appropriate for specified {@link Attack}.
	 * 
	 * @param attack {@link Attack} that is being analyzed
	 * @return {@link Response} to be executed for given {@link Attack}
	 */
	protected Response findAppropriateResponse(Attack attack) {
		DetectionPoint triggeringDetectionPoint = attack.getDetectionPoint();
		
		SearchCriteria criteria = new SearchCriteria().
				setUser(attack.getUser()).
				setDetectionPoint(triggeringDetectionPoint).
				setDetectionSystemIds(AppSensorServer.getInstance().getConfiguration().getRelatedDetectionSystems(attack.getDetectionSystemId()));
		
		//grab any existing responses
		Collection<Response> existingResponses = AppSensorServer.getInstance().getResponseStore().findResponses(criteria);
		
		String responseAction = null;
		Interval interval = null;
		
		Collection<Response> possibleResponses = findPossibleResponses(triggeringDetectionPoint);

		if (existingResponses == null || existingResponses.size() == 0) {
			//no responses yet, just grab first configured response from detection point
			Response response = possibleResponses.iterator().next();
			
			responseAction = response.getAction();
			interval = response.getInterval();
		} else {
			for (Response configuredResponse : possibleResponses) {
				responseAction = configuredResponse.getAction();
				interval = configuredResponse.getInterval();

				if (! isPreviousResponse(configuredResponse, existingResponses)) {
					//if we find that this response doesn't already exist, use it
					break;
				}
				
				//if we reach here, we will just use the last configured response (repeat last response)
			}
		}
		
		if(responseAction == null) {
			throw new IllegalArgumentException("No appropriate response was configured for this detection point: " + triggeringDetectionPoint.getId());
		}
		
		Response response = new Response();
		response.setUser(attack.getUser());
		response.setDetectionPoint(triggeringDetectionPoint);
		response.setTimestamp(attack.getTimestamp());
		response.setAction(responseAction);
		response.setInterval(interval);
		response.setDetectionSystemId(attack.getDetectionSystemId());
		
		return response;
	}
	
	/**
	 * Lookup configured {@link Response} objects for specified {@link DetectionPoint}
	 * 
	 * @param triggeringDetectionPoint {@link DetectionPoint} that triggered {@link Attack}
	 * @return collection of {@link Response} objects for given {@link DetectionPoint}
	 */
	protected Collection<Response> findPossibleResponses(DetectionPoint triggeringDetectionPoint) {
		Collection<Response> possibleResponses = new ArrayList<Response>();
		
		for (DetectionPoint configuredDetectionPoint : AppSensorServer.getInstance().getConfiguration().getDetectionPoints()) {
			if (configuredDetectionPoint.getId().equals(triggeringDetectionPoint.getId())) {
				possibleResponses = configuredDetectionPoint.getResponses();
				break;
			}
		}
		
		return possibleResponses;
	}
	
	/**
	 * Test a given {@link Response} to see if it's been executed before.
	 * 
	 * @param response {@link Response} to test to see if it's been executed before
	 * @param existingResponses set of previously executed {@link Response}s
	 * @return true if {@link Response} has been executed before
	 */
	protected boolean isPreviousResponse(Response response, Collection<Response> existingResponses) {
		boolean previousResponse = false;
		
		for (Response existingResponse : existingResponses) {
			if (response.getAction().equals(existingResponse.getAction())) {
				previousResponse = true;
			}
		}
		
		return previousResponse;
	}
}
